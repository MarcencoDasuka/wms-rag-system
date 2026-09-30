package com.isd.wms.dto.user;

import com.isd.wms.enums.Role;

public record UserResponse(
        Long id,
        String username,
        String email,
        Role userRole,
        Boolean isActive
) {}
