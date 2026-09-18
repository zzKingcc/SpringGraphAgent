package com.zzkingcc.stringer.example.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzkingcc.stringer.toolinstance.ToolInstanceContributor;
import com.zzkingcc.stringer.toolinstance.ToolRegistrar;
import com.zzkingcc.stringer.toolinstance.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 示例工具提供方 —— 让本示例进程同时扮演"工具实例"
 *
 * <h2>为什么示例要自带工具</h2>
 * <p>Stringer 服务端<b>不自带任何示例工具</b>（工具由部署方提供是既定设计）。若示例只做客户端，
 * 那么服务端注册表里就是 0 个工具，任何带 profile 的请求都会立刻被判 {@code 10004 域不存在}——
 * 页面点任何问题都只会得到一句错误，等于没法联调。</p>
 *
 * <p>因此示例自带 4 个工具，并把它们通过 {@code stringer-tool-instance} SDK
 * <b>周期整包注册</b>给服务端（而不是塞进服务端进程）。这样一来，一次联调就把三层链路全跑到了：</p>
 * <pre>
 * test.html → 示例应用(客户端 starter, 8080) → 服务端(9527, 编排 + 注册表)
 *                    ↑                                   │
 *                    └────── 回调 /stringer/invoke ────────┘   ← 工具其实跑在示例应用里
 * </pre>
 *
 * <h2>4 个工具覆盖三个演示维度</h2>
 * <table border="1">
 *   <tr><th>工具</th><th>域</th><th>演示什么</th></tr>
 *   <tr><td>{@code queryWeather}</td><td>不声明（全域可见）</td>
 *       <td>留空 profiles = 所有域都能看到</td></tr>
 *   <tr><td>{@code queryOrder}</td><td>{@code customer}</td>
 *       <td>客服域专属：管理域面板里它不出现</td></tr>
 *   <tr><td>{@code businessReport}</td><td>{@code admin}</td>
 *       <td>管理域专属：客服域问经营数据，模型手上没有这个能力</td></tr>
 *   <tr><td>{@code closeOrder}</td><td>{@code admin} + 需二次确认</td>
 *       <td>有副作用的写操作 → 触发 INTERRUPT，走 {@code resume} 审批链路</td></tr>
 * </table>
 *
 * <p><b>数据是内存里的假数据</b>，关单会真的改掉内存状态（所以再查一次能看出副作用）；
 * 进程重启即复位。真实接入时这里换成你的 Service / Mapper 调用即可——
 * SDK 只要求"给一段参数 JSON，还一段结果文本"。</p>
 * @author zzkingcc
 */
@Component
public class ExampleToolContributor implements ToolInstanceContributor {

    private static final Logger log = LoggerFactory.getLogger(ExampleToolContributor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 演示用订单表：订单号 → 状态（关单会改它，用来演示"工具有副作用"） */
    private static final Map<String, String> ORDERS = new ConcurrentHashMap<>(Map.of(
            "FR2024001", "已发货（预计明天送达）",
            "FR2024002", "待发货（已付款，仓库处理中）"));

    @Override
    public void contribute(ToolRegistrar registrar) {
        registrar
                // ① 全域可见：不声明 profiles
                .register(
                        ToolSpec.of("queryWeather", "查询某个城市当前天气。用户询问天气、气温、是否下雨时调用",
                                        ToolSpec.schema(Map.of(
                                                "city", Map.of("type", "string", "description", "城市名，如 杭州")),
                                                "city"))
                                .withCategory("通用").withSideEffect("READ"),
                        this::queryWeather)
                // ② 客服域专属
                .register(
                        ToolSpec.of("queryOrder", "按订单号查询订单状态。用户追问自己订单的发货 / 物流情况时调用",
                                        ToolSpec.schema(Map.of(
                                                "orderNo", Map.of("type", "string", "description", "订单号，如 FR2024001")),
                                                "orderNo"))
                                .withCategory("订单").withProfiles("customer").withSideEffect("READ"),
                        this::queryOrder)
                // ③ 管理域专属（客服域看不到它 —— 这是两个面板差异最直观的证据）
                .register(
                        ToolSpec.of("businessReport", "查询今日经营指标（订单量 / 成交额 / 退款 / 异常单数）。"
                                        + "仅管理视角可用；用户问今天经营情况、卖了多少时调用")
                                .withCategory("经营").withProfiles("admin").withSideEffect("READ"),
                        this::businessReport)
                // ④ 管理域专属 + 有副作用 ⇒ 需人工二次确认
                .register(
                        ToolSpec.of("closeOrder", "关闭一笔订单。仅在用户明确要求取消 / 关闭订单时调用，"
                                        + "必须给出原因",
                                        ToolSpec.schema(Map.of(
                                                        "orderNo", Map.of("type", "string", "description", "要关闭的订单号"),
                                                        "reason", Map.of("type", "string", "description", "关闭原因，如 用户申请退款")),
                                                "orderNo", "reason"))
                                .withCategory("订单").withProfiles("admin")
                                .withSideEffect("WRITE")
                                .withApproval("ALWAYS", "关单不可逆，需人工确认"),
                        this::closeOrder);
    }

    // ==================== 工具实现（真实接入时换成你的 Service） ====================

    private String queryWeather(String argumentsJson) {
        String city = text(argumentsJson, "city");
        log.info("[示例工具] queryWeather city={}", city);
        return city + "：多云转晴，26℃，东南风 2 级（示例数据）";
    }

    private String queryOrder(String argumentsJson) {
        String orderNo = text(argumentsJson, "orderNo");
        log.info("[示例工具] queryOrder orderNo={}", orderNo);
        String status = ORDERS.get(orderNo);
        if (status == null) {
            return "没有找到订单 " + orderNo + "。示例数据里只有 " + ORDERS.keySet();
        }
        return "订单 " + orderNo + " 当前状态：" + status;
    }

    private String businessReport(String argumentsJson) {
        log.info("[示例工具] businessReport");
        return "今日订单 128 单，成交额 ¥18,640，退款 3 单，异常 2 单（示例数据）";
    }

    private String closeOrder(String argumentsJson) {
        String orderNo = text(argumentsJson, "orderNo");
        String reason = text(argumentsJson, "reason");
        log.info("[示例工具] closeOrder orderNo={} reason={}", orderNo, reason);

        String current = ORDERS.get(orderNo);
        if (current == null) {
            return "没有找到订单 " + orderNo + "，无法关闭";
        }
        // 有副作用的工具应当自己防重：模型可能因上下文重复调用同一个工具
        if (current.startsWith("已关闭")) {
            return "订单 " + orderNo + " 已经是关闭状态，无需重复关单";
        }
        ORDERS.put(orderNo, "已关闭（原因：" + reason + "）");
        return "订单 " + orderNo + " 已关闭，原因：" + reason
                + "。如需确认可再次查询该订单状态。";
    }

    /**
     * 从参数 JSON 里取一个字符串字段。
     *
     * <p>模型偶尔会漏必填参数或给出非法 JSON，这里退化成"（未提供）"而不是抛异常——
     * 抛异常会让整次工具调用变成失败，而"参数缺失"本身就该是一次正常的工具返回。</p>
     */
    private static String text(String argumentsJson, String field) {
        try {
            JsonNode node = MAPPER.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String value = node.path(field).asText("");
            return value.isBlank() ? "（未提供）" : value;
        } catch (Exception e) {
            log.warn("[示例工具] 参数不是合法 JSON，按缺省处理: {}", argumentsJson);
            return "（未提供）";
        }
    }
}
