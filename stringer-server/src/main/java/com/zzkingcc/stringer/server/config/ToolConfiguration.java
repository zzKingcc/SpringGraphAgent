package com.zzkingcc.stringer.server.config;

import com.zzkingcc.stringer.api.tool.StringerToolProvider;
import com.zzkingcc.stringer.runtime.tool.AnnotatedToolScanner;
import com.zzkingcc.stringer.runtime.tool.ToolRegistry;
import com.zzkingcc.stringer.runtime.tool.ToolRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具装配：把服务端内部的工具注册进内核。
 * @author zzkingcc
 */
@Configuration
public class ToolConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ToolConfiguration.class);

    /**
     * 工具注册表：扫描所有工具提供者
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(ObjectProvider<StringerToolProvider> providers) {
        ToolRegistry registry = new ToolRegistry();
        int providerCount = 0;
        for (StringerToolProvider provider : providers) {
            providerCount++;
            registry.registerAll(AnnotatedToolScanner.scan(provider));
        }

        log.info("[工具装配] 扫描到 {} 个工具提供者，注册 {} 个工具；需授权工具 {} 个",
                providerCount, registry.size(), registry.toolsRequiringApproval());
        if (registry.isEmpty()) {
            // "一个工具都还没提供"是合法初始态（服务端不再自带示例工具），按约定只做状态陈述，不用 WARN：
            // 它既不是故障也不影响启动，WARN 只会让每次冷启动都像是出了问题。
            log.info("[工具装配] 未注册任何工具 —— 实现 StringerToolProvider 并在方法上标注 @StringerTool 即可注册；"
                    + "在此之前「域空间」会显示 0 / 未注册；因 knownProfiles 为空，acceptsProfile 对任何域返回 true"
                    + "（即接受任意域、但可见工具集为空），不会被判为域不存在（10004）");
        }
        return registry;
    }

    /**
     * 工具路由器（内核唯一的能力调用入口）
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolRouter toolRouter(ToolRegistry toolRegistry) {
        return new ToolRouter(toolRegistry);
    }
}
