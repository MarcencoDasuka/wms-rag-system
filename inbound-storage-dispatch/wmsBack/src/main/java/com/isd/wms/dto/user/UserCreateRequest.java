package com.isd.wms.dto.user;

import com.isd.wms.enums.Role;
import com.isd.wms.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Email(message = "Invalid email format.") String email,
        @NotNull Role userRole
) {
}
