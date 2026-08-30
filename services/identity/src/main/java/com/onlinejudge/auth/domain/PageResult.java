package com.onlinejudge.auth.domain;

import java.util.List;

public record PageResult<T>(List<T> records, long total) {
}
