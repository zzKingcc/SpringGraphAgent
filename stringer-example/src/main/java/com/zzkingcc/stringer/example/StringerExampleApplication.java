package com.zzkingcc.stringer.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Stringer 示例 / 联调入口
 *
 * <p>这是本仓库<strong>唯一</strong>的可运行应用：固定 Spring Boot 3.3.4，
 * 只包含一层 Controller（{@code example.controller}）与配套的异常处理，
 * 用于本地启动后对接 {@code test.html} 做手工联调。</p>
 *
 * <p>Stringer 已升级为真正的 Spring Boot 自动配置（{@code StringerAutoConfiguration}
 * 注册于 {@code AutoConfiguration.imports}）：引入 starter 即自动装配，无需
 * {@code @ComponentScan} 覆盖内部包；本应用只扫描自身 {@code example} 包即可。</p>
 *
 * <h2>本示例同时扮演两个角色</h2>
 * <ul>
 *   <li><b>客户端</b>（{@code stringer-spring-boot-starter}）：调 AI 能力。注入 {@link com.zzkingcc.stringer.api.agent.AgentService}
 *       即可，编排 / 工具 / 模型 / 存储全在服务端；</li>
 *   <li><b>工具实例</b>（{@code stringer-tool-instance}）：用 {@code example.tools.ExampleToolContributor}
 *       声明 4 个演示工具，周期整包注册给服务端，并对外暴露 {@code /stringer/invoke}
 *       供服务端回调执行。</li>
 * </ul>
 * <p>两个角色合在一个进程里是刻意的：这样"调用 AI"与"提供工具"两条链路一次就能跑通，
 * 不必额外部署一个进程。真实场景里工具通常属于业务系统，而调 AI 的可能另有其人。</p>
 *
 * <h2>启动顺序（硬约束）</h2>
 * <p><b>先起服务端（9527），再起本应用（8080）。</b>客户端 starter 在启动期会做连通性探测，
 * 连不上服务端会直接中断启动（与 Redis / Nacos 的 fail-fast 一致）。起来后可在管控台
 * {@code http://localhost:9527/admin.html} 的「在线实例」页确认本实例已注册、其工具已进注册表。</p>
 *
 * <p><b>本应用不需要任何环境变量</b>：服务端侧的模型 / ES / Redis 都在管控台配置；
 * 这里只有服务端地址与接入账号可覆盖（{@code STRINGER_SERVER_HOST} / {@code STRINGER_SERVER_PORT} /
 * {@code STRINGER_SERVER_USERNAME} / {@code STRINGER_SERVER_PASSWORD}）。
 * 工具回流地址也不用配：本应用与服务端同机，由 SDK 按本进程端口推导。</p>
 * @author zzkingcc
 */
@Slf4j
@SpringBootApplication
public class StringerExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(StringerExampleApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Stringer 示例应用启动成功，联调页面：http://localhost:8080/test.html");
    }
}
