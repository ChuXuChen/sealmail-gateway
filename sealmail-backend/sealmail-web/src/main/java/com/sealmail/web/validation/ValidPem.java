package com.sealmail.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PemContentValidator.class)
public @interface ValidPem {

    String message() default "PEM格式不正确";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
