package com.zzkingcc.stringer.server.settings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 域提示词设置（落盘 {@code config/profiles.json}）
 * @author zzkingcc
 */
public class ProfileSettings {

    /** 公共基线：所有域共享 */
    private String base = "";

    /** 域 → 该域的差异提示词 */
    private Map<String, String> profiles = new LinkedHashMap<>();

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base == null ? "" : base;
    }

    public Map<String, String> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, String> profiles) {
        this.profiles = profiles == null ? new LinkedHashMap<>() : new LinkedHashMap<>(profiles);
    }

    /** 指定域的差异片段；未配置返回 {@code null} */
    public String profilePrompt(String profile) {
        return profiles.get(profile);
    }
}
