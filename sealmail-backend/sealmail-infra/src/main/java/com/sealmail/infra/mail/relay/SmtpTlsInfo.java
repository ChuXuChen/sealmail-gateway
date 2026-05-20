package com.sealmail.infra.mail.relay;

record SmtpTlsInfo(String protocol, String cipher, String peerCertificateFingerprint) {
}
