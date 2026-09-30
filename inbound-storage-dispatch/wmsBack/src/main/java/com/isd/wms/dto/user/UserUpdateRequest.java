package com.isd.wms.dto.user;

import com.isd.wms.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @NotBlank @Size(max=50) String username,
    @NotNull Role userRole
) {
}
