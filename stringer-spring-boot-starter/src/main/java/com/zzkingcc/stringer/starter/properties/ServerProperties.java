package com.zzkingcc.stringer.starter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stringer 服务端连接配置（{@code stringer.server.*}）
 *
 * <p>地址与账号只写一份：服务端只有一个账号，工具注册表也只有一份，
 * 客户端与工具实例指向不同服务端属于破坏性配置（工具注册在 A、对话发起在 B 时，
 * B 的工具表为空），因此不存在"两侧各自一份连接信息"的用法。</p>
 *
 * @author zzkingcc
 */
@Data
@ConfigurationProperties(prefix = "stringer.server")
public class ServerProperties {

    /** 服务端主机（主机名或 IP，不含协议与端口） */
    private String host = "localhost";

    /** 服务端端口 */
    private int port = 9527;

    /** 接入账号：服务端账号 */
    private String username = "stringer";

    /** 接入密码，与 {@link #username} 配对 */
    private String password = "stringer";

    /** 服务端 baseUrl（{@code http://host:port}） */
    public String getServerUrl() {
        return "http://" + host + ":" + port;
    }
}
