package com.sealmail.edge.smtp;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface RelayTargetResolver {
    Optional<RelayTarget> resolve(List<String> recipients);
}
