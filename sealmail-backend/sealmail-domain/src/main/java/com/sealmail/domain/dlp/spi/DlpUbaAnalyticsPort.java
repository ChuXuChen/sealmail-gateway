package com.sealmail.domain.dlp.spi;

import com.sealmail.domain.dlp.DlpUbaSenderRisk;

import java.util.List;

public interface DlpUbaAnalyticsPort {

    List<DlpUbaSenderRisk> listSenderRisks(int limit);
}
