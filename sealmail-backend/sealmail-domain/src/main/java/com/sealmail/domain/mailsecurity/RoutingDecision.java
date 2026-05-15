package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;

import java.util.Collections;
import java.util.List;

public abstract class RoutingDecision {

    private RoutingDecision() {
    }

    public static final class OutboundEncrypt extends RoutingDecision {
        private final List<EmailAddress> recipients;

        public OutboundEncrypt(List<EmailAddress> recipients) {
            this.recipients = recipients != null ? recipients : Collections.emptyList();
        }

        public List<EmailAddress> getRecipients() {
            return recipients;
        }
    }

    public static final class OutboundSign extends RoutingDecision {
        private final List<EmailAddress> recipients;

        public OutboundSign(List<EmailAddress> recipients) {
            this.recipients = recipients != null ? recipients : Collections.emptyList();
        }

        public List<EmailAddress> getRecipients() {
            return recipients;
        }
    }

    public static final class InboundDecrypt extends RoutingDecision {
    }

    public static final class InboundVerify extends RoutingDecision {
    }

    public static final class PassThrough extends RoutingDecision {
    }

    public static final class Quarantine extends RoutingDecision {
        private final QuarantineReason reason;
        private final String detail;

        public Quarantine(QuarantineReason reason, String detail) {
            if (reason == null) {
                throw new IllegalArgumentException("Quarantine reason cannot be null");
            }
            this.reason = reason;
            this.detail = detail;
        }

        public QuarantineReason getReason() {
            return reason;
        }

        public String getDetail() {
            return detail;
        }
    }
}
