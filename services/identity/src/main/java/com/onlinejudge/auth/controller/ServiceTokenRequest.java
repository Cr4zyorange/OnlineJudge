package com.onlinejudge.auth.controller;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonDeserialize(using = StrictServiceTokenRequestDeserializer.class)
public record ServiceTokenRequest(String audience, List<String> scopes) {
}
