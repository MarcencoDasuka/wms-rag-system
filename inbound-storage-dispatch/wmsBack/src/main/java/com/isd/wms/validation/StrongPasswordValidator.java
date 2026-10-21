package com.isd.wms.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    public static final Pattern PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._#-])[A-Za-z\\d@$!%*?&._#-]{8,64}$"
    );

    @Override
    public void initialize(StrongPassword constraintAnnotation) {
    }

    public static boolean isStrongPassword(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        return PATTERN.matcher(password).matches();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        return isStrongPassword(password);
    }
}
