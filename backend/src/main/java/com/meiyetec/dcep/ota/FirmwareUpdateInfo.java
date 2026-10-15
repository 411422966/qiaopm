package com.meiyetec.dcep.ota;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FirmwareUpdateInfo {
    private Long packageId;
    private String version;
    private String filePath;
    private String sha256;
    private Long fileSize;
    private String signature;
    private String signAlgorithm;
    private boolean mandatory;
}
