package com.onlinejudge.auth.controller;

import java.util.List;

public record ServiceTokenRequest(String audience, List<String> scopes) {
}
