package com.jokerhub.paper.plugin.orzmc.infra.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Hangar API v1 只读客户端（查询项目指定通道的最新版本与下载信息）。
 *
 * <p>OrzMC 发布通道与 CI 对应：tag → {@code release}（正式版），main push → {@code beta}
 * （{@code -dev} 构建）。查询响应的 {@code result[0]} 即通道内最新版（服务端已按新→旧排序），
 * 取 {@code limit=1} 即可。下载信息走 CDN 直链 + sha256，供插件自更新下载校验。</p>
 */
public final class HangarClient {

    /** OrzMC 在 Hangar 的项目完整 slug（owner/project）。 */
    public static final String PROJECT_SLUG = "OrzMC/OrzMC";

    /** Hangar API v1 项目版本端点基址。 */
    public static final String API_BASE = "https://hangar.papermc.io/api/v1/projects/" + PROJECT_SLUG;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;

    public HangarClient() {
        this(API_BASE);
    }

    /** 测试可注入基址（mock 服务）。 */
    public HangarClient(String baseUrl) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? API_BASE : baseUrl;
    }

    /**
     * 通道内最新版本（无版本时为空）。
     *
     * @param version 版本串（与 CI 发布名一致，如 1.0.24-dev.360）
     * @param publishedAt 发布时间（供新旧比对）
     * @param fileName 平台原始文件名（fileInfo.name，如 OrzMC-1.0.24.jar；落盘须保持该名）
     * @param downloadUrl CDN 下载直链
     * @param sha256 下载内容的 sha256（落盘前校验）
     */
    public record LatestVersion(
            String version, Instant publishedAt, String fileName, String downloadUrl, String sha256) {}

    /** 查询指定通道最新版本。网络/解析失败以 {@link CompletionException} 抛出（调用方兜底）。 */
    public CompletableFuture<Optional<LatestVersion>> latest(String channel) {
        String url = versionsUrl(channel);
        return AsyncHttp.get(url, Map.of("Accept", "application/json"), CONNECT_TIMEOUT, REQUEST_TIMEOUT, 2)
                .thenApply(response -> parseLatest(response.body(), url));
    }

    /** 组装版本列表查询 URL（包内可见：单测直接断言，避免真实外呼）。 */
    String versionsUrl(String channel) {
        return baseUrl + "/versions?limit=1&channel=" + URLEncoder.encode(channel, StandardCharsets.UTF_8);
    }

    /**
     * 解析版本列表响应。结构（Hangar API v1）：
     * {@code {"result":[{"name":..., "createdAt":..., "downloads":{"PAPER":{
     * "fileInfo":{"name":...,"sha256Hash":...},"externalUrl":...,"downloadUrl":...}}}]}}
     */
    Optional<LatestVersion> parseLatest(String body, String url) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray result = root.has("result") ? root.getAsJsonArray("result") : null;
        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }
        JsonObject first = result.get(0).getAsJsonObject();
        String version = first.has("name") ? first.get("name").getAsString() : null;
        Instant publishedAt = first.has("createdAt") && !first.get("createdAt").isJsonNull()
                ? parseInstant(first.get("createdAt").getAsString())
                : null;
        JsonObject file = first.has("downloads") ? first.getAsJsonObject("downloads") : null;
        JsonObject paper = file != null && file.has("PAPER") ? file.getAsJsonObject("PAPER") : null;
        if (version == null || paper == null) {
            return Optional.empty();
        }
        String downloadUrl =
                paper.has("externalUrl") && !paper.get("externalUrl").isJsonNull()
                        ? paper.get("externalUrl").getAsString()
                        : paper.has("downloadUrl") && !paper.get("downloadUrl").isJsonNull()
                                ? paper.get("downloadUrl").getAsString()
                                : null;
        String fileName = null;
        String sha256 = null;
        if (paper.has("fileInfo") && !paper.get("fileInfo").isJsonNull()) {
            JsonObject fileInfo = paper.getAsJsonObject("fileInfo");
            fileName = fileInfo.has("name") && !fileInfo.get("name").isJsonNull()
                    ? fileInfo.get("name").getAsString()
                    : null;
            sha256 = fileInfo.has("sha256Hash") ? fileInfo.get("sha256Hash").getAsString() : null;
        }
        return Optional.of(new LatestVersion(version, publishedAt, fileName, downloadUrl, sha256));
    }

    private static Instant parseInstant(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
