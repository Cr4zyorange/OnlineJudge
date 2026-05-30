package com.onlinejudge.auth.controller;

public record PasswordChangeRequest(
        String oldPassword,
        String newPassword
) {
}
