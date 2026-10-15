package com.meiyetec.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "meiyetec.blockchain")
public class BlockchainChainConfig {

    private boolean enabled = true;

    private int defaultDifficulty = 2;

    private Map<String, String> recordTypeChainMap = new LinkedHashMap<String, String>() {{
        put("ORDER", "meiyetec-main");
        put("CONSENT", "meiyetec-main");
        put("PRODUCT", "meiyetec-cosmetic");
        put("DRUG", "meiyetec-drug");
        put("DEVICE", "meiyetec-device");
    }};

    private Map<String, Integer> chainDifficulty = new LinkedHashMap<String, Integer>() {{
        put("meiyetec-main", 2);
        put("meiyetec-cosmetic", 2);
        put("meiyetec-drug", 3);
        put("meiyetec-device", 3);
    }};

    private boolean integrityCheckEnabled = true;

    private boolean fabricEnabled = false;
    private String fabricChannelName = "mychannel";
    private String fabricChaincodeName = "meiyetec-cc";
}
