package com.meiyetec.dcep.crypto;

import com.meiyetec.dcep.crypto.impl.BouncyCastleSmCryptoProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DCEP-03 国密算法 Spring 自动装配
 *
 * <p>激活方式：项目引入 bcprov-jdk18on 依赖即可使用软件实现；
 * 未来对接 HSM 时，只需实现 {@link SmCryptoProvider} 接口并标注
 * {@code @Primary} 或移除本 {@code @ConditionalOnMissingBean} 即可替换。
 */
@Slf4j
@Configuration
public class SmCryptoConfig {

    @Bean
    @ConditionalOnMissingBean(SmCryptoProvider.class)
    public SmCryptoProvider smCryptoProvider() {
        log.info("[DCEP-03] SmCryptoProvider 初始化：使用 BouncyCastle 软件实现（开发联调用）");
        return new BouncyCastleSmCryptoProvider();
    }
}
