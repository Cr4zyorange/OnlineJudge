package com.onlinejudge.auth.controller;

import java.util.List;

public record PermissionIdsRequest(List<Long> permissionIds) {
}
