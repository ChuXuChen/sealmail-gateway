package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.shared.model.EmailAddress;

import java.util.List;
import java.util.Optional;

public interface DeliveryRouteResolver {

    Optional<DeliveryRoute> resolve(List<EmailAddress> recipients);
}
