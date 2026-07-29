package com.cattlefarm.admin.auth;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String userId,
        String farmId,
        String realName,
        List<String> roles
) {
}
