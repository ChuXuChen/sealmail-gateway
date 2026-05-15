package com.sealmail.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class PemContentValidator implements ConstraintValidator<ValidPem, String> {

    @Override
    public boolean isValid(String pem, ConstraintValidatorContext context) {
        if (pem == null) {
            return true;  // @NotBlank处理空值
        }

        String trimmed = pem.trim();
        return trimmed.contains("-----BEGIN") && trimmed.contains("-----END");
    }
}
