package com.packflow.app.security;

import com.packflow.app.user.Role;

public record JwtPrincipal(String username, Role role, long tokenVersion) {
}
