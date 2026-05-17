/**
 * Legacy mail authentication implementation retained as migration input for the
 * modern domain-driven mailauth subsystem.
 *
 * <p>New DKIM/SPF/DMARC behavior should be added behind domain ports under
 * {@code com.sealmail.domain.mailauth} and infrastructure adapters under the
 * new mailauth boundary, not by expanding this package.</p>
 */
@Deprecated(forRemoval = false)
package com.sealmail.infra.mail.auth;
