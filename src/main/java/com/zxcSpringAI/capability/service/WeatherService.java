package com.zxcSpringAI.capability.service;

import org.springframework.stereotype.Component;

/**
 * 天气查询能力服务
 *
 * <p>纯业务逻辑,无 Agent 框架依赖。提供公司所在地(广州)的天气查询能力,
 * 由 agent 层的 LocalToolWrappers 用 @Tool 注解包装后暴露给 LLM。</p>
 */
@Component
public class WeatherService {

    /** 公司所在地 */
    private static final String CITY = "广州";

    /**
     * 查询当前天气状况
     */
    public String queryWeather() {
        return CITY + "当前天气：多云转晴，气温 26°C ~ 33°C，东南风 2-3 级，湿度 72%";
    }

    /**
     * 查询今日最高温度
     */
    public String queryMaxTemperature() {
        return CITY + "今日最高温度：33°C";
    }

    /**
     * 查询今日最低温度
     */
    public String queryMinTemperature() {
        return CITY + "今日最低温度：26°C";
    }

    /**
     * 查询当前湿度
     */
    public String queryHumidity() {
        return CITY + "当前湿度：72%";
    }

    /**
     * 查询当前风力
     */
    public String queryWind() {
        return CITY + "当前风力：东南风 2-3 级";
    }
}
