package com.isd.wms.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;

public record AiChatRequest(@NotNull @NotBlank @NonNull String message) {}
