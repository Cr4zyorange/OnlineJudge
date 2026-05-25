package com.onlinejudge.common.web;

import java.util.List;

public record PageResponse<T>(List<T> list, long total, int page, int size) {
}
