package com.zzkingcc.stringer.server.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zzkingcc.stringer.common.util.AtomicFiles;
import com.zzkingcc.stringer.server.env.StorageLocations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 域提示词的持久化存储（{@code config/profiles.json}）
 * @author zzkingcc
 */
@Slf4j
@Component
public class ProfileSettingsStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path settingsFile;

    public ProfileSettingsStore(StorageLocations storage) {
        this.settingsFile = storage.settingsDir().resolve("profiles.json");
    }

    /**
     * 读取设置；文件不存在或解析失败时返回空设置（不阻断启动，可进管控台修正）。
     */
    public ProfileSettings load() {
        if (!Files.exists(settingsFile)) {
            log.debug("[域提示词] 未找到设置文件 {}，回落 yaml（stringer.ai.prompt）",
                    settingsFile.toAbsolutePath());
            return new ProfileSettings();
        }
        try {
            String json = Files.readString(settingsFile, StandardCharsets.UTF_8);
            ProfileSettings settings = MAPPER.readValue(json, ProfileSettings.class);
            if (settings == null) {
                return new ProfileSettings();
            }
            log.debug("[域提示词] 已加载设置文件 {}：基线 {} 字符，域差异 {} 个",
                    settingsFile.toAbsolutePath(),
                    settings.getBase() == null ? 0 : settings.getBase().length(),
                    settings.getProfiles().size());
            return settings;
        } catch (IOException e) {
            log.error("[域提示词] 读取设置文件失败，将以空设置继续（可进管控台修正）: {}",
                    settingsFile.toAbsolutePath(), e);
            return new ProfileSettings();
        }
    }

    /**
     * 保存设置（覆盖写）。
     *
     * @throws IllegalStateException 写盘失败。必须让调用方感知，静默失败会让用户以为已生效
     */
    public void save(ProfileSettings settings) {
        try {
            AtomicFiles.write(settingsFile, MAPPER.writeValueAsBytes(settings));
            log.info("[域提示词] 已保存到 {}：基线 {} 字符，域差异 {} 个",
                    settingsFile.toAbsolutePath(),
                    settings.getBase() == null ? 0 : settings.getBase().length(),
                    settings.getProfiles().size());
        } catch (IOException e) {
            throw new IllegalStateException("域提示词保存失败：" + settingsFile.toAbsolutePath(), e);
        }
    }

    /** 设置文件路径（供管控台展示，便于运维确认落盘位置） */
    public String filePath() {
        return settingsFile.toAbsolutePath().toString();
    }
}
