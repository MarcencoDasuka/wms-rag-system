package com.isd.wms.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default "Password must be 8-64 characters and contain at least one uppercase letter, one lowercase letter, one digit and one special character (@$!%*?&_#)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}