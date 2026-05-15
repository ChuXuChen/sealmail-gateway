package com.sealmail.app.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseQuarantineRequest {

    private String releasedBy;

    private String comment;

    @Builder.Default
    private Boolean force = Boolean.FALSE;

    @Builder.Default
    private Boolean encryptBeforeRelease = Boolean.FALSE;
}
