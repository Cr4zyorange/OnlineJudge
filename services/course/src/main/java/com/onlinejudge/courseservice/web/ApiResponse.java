package com.onlinejudge.courseservice.web;

public record ApiResponse<T>(String code, String message, T data) {
    public static <T> ApiResponse<T> created(T data) { return new ApiResponse<>("0", "success", data); }
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>("0", "success", data); }
}
