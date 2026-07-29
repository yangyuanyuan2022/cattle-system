package com.cattlefarm.admin.auth;

import java.util.List;

public record CurrentUserResponse(
        String userId,
        String farmId,
        String username,
        String realName,
        List<String> roles,
        List<String> permissions
) {
}
