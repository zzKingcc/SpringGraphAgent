package com.zzkingcc.stringer.server.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Stringer 服务端总装自动配置。
 * @author zzkingcc
 */
@AutoConfiguration
@ComponentScan({
        "com.zzkingcc.stringer.runtime.cancellation",
        "com.zzkingcc.stringer.domain.capability.knowledge",
        "com.zzkingcc.stringer.server.tools",
        "com.zzkingcc.stringer.server.support"
})
@Import({
        ConfigValidationConfiguration.class,
        AiModelConfiguration.class,
        RedisClientConfiguration.class,
        MemoryConfiguration.class,
        EsClientConfiguration.class,
        RetrievalConfiguration.class,
        ToolConfiguration.class,
        InstanceConfiguration.class,
        GraphConfiguration.class
})
public class StringerServerAutoConfiguration {
}
