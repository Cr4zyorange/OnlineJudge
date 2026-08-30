package com.onlinejudge.auth.service;

import com.onlinejudge.auth.exception.AuthApiException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PasswordSecurityService {
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordCredential hash(String rawPassword) {
        validatePassword(rawPassword);
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        return new PasswordCredential(hash(rawPassword, salt), Base64.getEncoder().encodeToString(salt));
    }

    public boolean matches(String rawPassword, String expectedHash, String encodedSalt) {
        if (rawPassword == null || expectedHash == null || encodedSalt == null) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(encodedSalt);
        return expectedHash.equals(hash(rawPassword, salt));
    }

    public void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8 || rawPassword.length() > 72) {
            throw AuthApiException.badRequest("密码长度应为 8 到 72 位");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw AuthApiException.badRequest("密码必须同时包含字母和数字");
        }
    }

    private String hash(String rawPassword, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, ITERATIONS, KEY_BITS);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("密码哈希失败", exception);
        }
    }

    public record PasswordCredential(String passwordHash, String passwordSalt) {
    }
}
