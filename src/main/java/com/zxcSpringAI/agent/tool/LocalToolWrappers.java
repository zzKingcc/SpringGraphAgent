package com.zxcSpringAI.agent.tool;

import com.zxcSpringAI.capability.service.RagService;
import com.zxcSpringAI.capability.service.WeatherService;
import com.zxcSpringAI.common.util.TokenUsageTracker;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 本地工具包装器
 *
 * <p>用 @Tool 注解包装 capability 层的 Service,注册为 Agent 可用工具。
 * capability 层保持纯净(无 Agent 框架依赖),本类负责"协议适配":
 * 将纯 Java Service 方法包装成 LangChain4j 的 @Tool 格式,供 LLM 识别和调用。</p>
 *
 * <p>Token 用量统计统一在此层记录,capability Service 内部不碰 Tracker。</p>
 */
@Component
public class LocalToolWrappers {

    private final WeatherService weatherService;
    private final RagService ragService;

    public LocalToolWrappers(WeatherService weatherService, RagService ragService) {
        this.weatherService = weatherService;
        this.ragService = ragService;
    }

    // ==================== 天气工具 ====================

    @Tool("查询喜羊羊公司所在地（广州）的当前天气状况，当用户问'今天天气怎么样'、'外面热不热'、'需要带伞吗'等天气相关问题时调用")
    public String queryWeather() {
        String result = weatherService.queryWeather();
        TokenUsageTracker.recordToolCall("queryWeather", "", result);
        return result;
    }

    @Tool("查询喜羊羊公司所在地（广州）今日最高温度，当用户问'今天最高多少度'、'热不热'等温度相关问题时调用")
    public String queryMaxTemperature() {
        String result = weatherService.queryMaxTemperature();
        TokenUsageTracker.recordToolCall("queryMaxTemperature", "", result);
        return result;
    }

    @Tool("查询喜羊羊公司所在地（广州）今日最低温度，当用户问'今天最低多少度'、'晚上冷不冷'等温度相关问题时调用")
    public String queryMinTemperature() {
        String result = weatherService.queryMinTemperature();
        TokenUsageTracker.recordToolCall("queryMinTemperature", "", result);
        return result;
    }

    @Tool("查询喜羊羊公司所在地（广州）当前湿度，当用户问'潮不潮湿'、'湿度多少'等湿度相关问题时调用")
    public String queryHumidity() {
        String result = weatherService.queryHumidity();
        TokenUsageTracker.recordToolCall("queryHumidity", "", result);
        return result;
    }

    @Tool("查询喜羊羊公司所在地（广州）当前风力，当用户问'风大不大'、'几级风'等风力相关问题时调用")
    public String queryWind() {
        String result = weatherService.queryWind();
        TokenUsageTracker.recordToolCall("queryWind", "", result);
        return result;
    }

    // ==================== 知识库检索工具 ====================

    @Tool("从喜羊羊公司知识库中检索业务相关内容。当用户询问公司产品、服务、政策、规章制度、业务流程、操作指南等知识性问题时调用此工具，参数为用户问题的关键词或完整问题描述。非知识性问题（闲聊、天气、问候等）不要调用")
    public String searchKnowledgeBase(@P("检索关键词或问题") String keyword) {
        String result = ragService.searchKnowledgeBase(keyword);
        TokenUsageTracker.recordToolCall("searchKnowledgeBase", keyword, result);
        return result;
    }
}
