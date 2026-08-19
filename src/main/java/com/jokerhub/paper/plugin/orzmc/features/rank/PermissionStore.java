package com.jokerhub.paper.plugin.orzmc.features.rank;

import com.jokerhub.paper.plugin.orzmc.features.review.ReviewRequest;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewStore;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 权限模块统一存储（permission.yml，两段式）。
 *
 * <pre>
 * permission.yml
 * ├── config:  member-threshold-hours: 10    # 静态配置节
 * └── reviews: requests.&lt;id&gt;: {...}         # 审核记录节（运行时）
 * </pre>
 *
 * <p>同时实现 {@link RankStore}（stats 时长读取）与 {@link ReviewStore}
 * （reviews 节），一个文件统一管理，替代原 ranks.yml 单文件存储。
 * config 节静态读取，reviews 节 markAlwaysSave 保证频繁写不丢。
 * <b>权限组状态不在本地存储</b>——LP track 为唯一事实源（见 {@code RankPromoter}）。</p>
 *
 * <p>时长数据直接读 Minecraft 服务器原生统计文件
 * {@code <主世界>/players/stats/<uuid>.json} 的 {@code minecraft:play_time}（tick），
 * 与服务器同源、玩家离线也可读、无自计算误差。</p>
 */
public final class PermissionStore implements RankStore, ReviewStore {

    private static final String FILE = "permission";
    private static final String CONFIG_SECTION = "config";
    private static final String REVIEWS_SECTION = "reviews.requests";
    private static final String PLAY_TIME_KEY = "minecraft:play_time";
    private static final int DEFAULT_MEMBER_THRESHOLD_HOURS = 10;

    /**
     * 每玩家结案历史保留上限。
     *
     * <p>审核记录只增不删会让 permission.yml 无限增长（每次提交/审核全量写盘
     * O(N)）。PENDING 天然有界（防重复提交：每玩家每类型最多 1 条待审），
     * 只需裁剪结案记录（APPROVED/REJECTED/CANCELLED）：保留最近
     * {@link #MAX_HISTORY_PER_PLAYER} 条，超出的删最旧。</p>
     */
    private static final int MAX_HISTORY_PER_PLAYER = 10;

    /**
     * 审核记录写盘锁。
     *
     * <p>approve 最终化在 global 线程写，reject/cancel/submit 在 region 线程写——共享的
     * FileConfiguration（cfg.set + createSection + trimHistory 遍历）无同步会丢更新或
     * ConcurrentModificationException，saveConfig 并发写同一文件可能损坏 YAML。
     * 锁覆盖 writeRequest + trimHistory + saveConfig 整个临界区，跨线程串行化。</p>
     */
    private final Object saveLock = new Object();

    private final ConfigService configService;

    public PermissionStore(ConfigService configService) {
        this.configService = configService;
    }

    // ---- 静态配置节 ----

    /** 晋升 member 阈值（小时），从 config 节读取，缺省 10。 */
    public int memberThresholdHours() {
        FileConfiguration cfg = configService.getConfig(FILE);
        return cfg.getInt(CONFIG_SECTION + ".member-threshold-hours", DEFAULT_MEMBER_THRESHOLD_HOURS);
    }

    // ---- RankStore：stats 时长（权限状态由 LP track 持有，本地不存）----

    @Override
    public long getPlaytimeMinutes(UUID playerId) {
        Path statsFile = statsDirectory().resolve(playerId + ".json");
        return readPlayTimeTicks(statsFile) / 1200L; // 20 tick/s × 60 s/min
    }

    // ---- ReviewStore：reviews 节 ----

    @Override
    public void save(ReviewRequest request) {
        // 跨线程串行化 write+save 临界区（global 线程 finalize / region 线程 submit/cancel/reject 并发）
        synchronized (saveLock) {
            FileConfiguration cfg = configService.getConfig(FILE);
            writeRequest(cfg, request);
            trimHistory(cfg, request.applicantId());
            configService.saveConfig(FILE);
        }
    }

    @Override
    public Optional<ReviewRequest> findById(String id) {
        FileConfiguration cfg = configService.getConfig(FILE);
        String path = REVIEWS_SECTION + "." + id;
        if (!cfg.contains(path + ".type")) {
            return Optional.empty();
        }
        return readRequest(cfg, path);
    }

    @Override
    public List<ReviewRequest> listPending() {
        FileConfiguration cfg = configService.getConfig(FILE);
        ConfigurationSection section = cfg.getConfigurationSection(REVIEWS_SECTION);
        if (section == null) {
            return List.of();
        }
        List<ReviewRequest> pending = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            readRequest(cfg, REVIEWS_SECTION + "." + id).ifPresent(request -> {
                if (request.status() == ReviewRequest.Status.PENDING) {
                    pending.add(request);
                }
            });
        }
        pending.sort(Comparator.comparingLong(ReviewRequest::createdAt));
        return pending;
    }

    @Override
    public List<ReviewRequest> listByApplicant(UUID applicantId) {
        FileConfiguration cfg = configService.getConfig(FILE);
        ConfigurationSection section = cfg.getConfigurationSection(REVIEWS_SECTION);
        if (section == null) {
            return List.of();
        }
        List<ReviewRequest> found = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            readRequest(cfg, REVIEWS_SECTION + "." + id).ifPresent(request -> {
                if (request.applicantId().equals(applicantId)) {
                    found.add(request);
                }
            });
        }
        found.sort(Comparator.comparingLong(ReviewRequest::createdAt));
        return found;
    }

    @Override
    public Optional<ReviewRequest> pendingFor(String typeId, UUID applicantId) {
        FileConfiguration cfg = configService.getConfig(FILE);
        ConfigurationSection section = cfg.getConfigurationSection(REVIEWS_SECTION);
        if (section == null) {
            return Optional.empty();
        }
        for (String id : section.getKeys(false)) {
            Optional<ReviewRequest> maybe = readRequest(cfg, REVIEWS_SECTION + "." + id);
            if (maybe.isPresent()) {
                ReviewRequest request = maybe.get();
                if (request.status() == ReviewRequest.Status.PENDING
                        && request.typeId().equals(typeId)
                        && request.applicantId().equals(applicantId)) {
                    return Optional.of(request);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean hasPending(String typeId, UUID applicantId) {
        return pendingFor(typeId, applicantId).isPresent();
    }

    /**
     * 结案历史裁剪：每玩家最多保留 {@link #MAX_HISTORY_PER_PLAYER} 条记录。
     *
     * <p>PENDING 永不删（防重复提交已保证其有界）；超限时按 createdAt 删最旧的
     * 结案记录（APPROVED/REJECTED/CANCELLED）。在 save() 写盘前调用，一次落盘。</p>
     */
    private void trimHistory(FileConfiguration cfg, UUID applicantId) {
        ConfigurationSection section = cfg.getConfigurationSection(REVIEWS_SECTION);
        if (section == null) {
            return;
        }
        List<Map.Entry<String, Long>> closed = new ArrayList<>();
        int total = 0;
        for (String id : section.getKeys(false)) {
            Optional<ReviewRequest> maybe = readRequest(cfg, REVIEWS_SECTION + "." + id);
            if (maybe.isEmpty()) {
                continue;
            }
            ReviewRequest request = maybe.get();
            if (!request.applicantId().equals(applicantId)) {
                continue;
            }
            total++;
            if (request.status() != ReviewRequest.Status.PENDING) {
                closed.add(Map.entry(id, request.createdAt()));
            }
        }
        int overflow = total - MAX_HISTORY_PER_PLAYER;
        if (overflow <= 0 || closed.isEmpty()) {
            return;
        }
        closed.sort(Comparator.comparingLong(Map.Entry::getValue));
        for (int i = 0; i < overflow && i < closed.size(); i++) {
            cfg.set(REVIEWS_SECTION + "." + closed.get(i).getKey(), null);
        }
    }

    /** 写一条审核记录到 cfg（不落盘，调用方负责 saveConfig）。 */
    private void writeRequest(FileConfiguration cfg, ReviewRequest request) {
        String path = REVIEWS_SECTION + "." + request.id();
        cfg.set(path + ".type", request.typeId());
        cfg.set(path + ".applicant", request.applicantId().toString());
        if (request.data() != null && !request.data().isEmpty()) {
            ConfigurationSection dataSection = cfg.createSection(path + ".data");
            request.data().forEach(dataSection::set);
        }
        cfg.set(path + ".status", request.status().name());
        cfg.set(path + ".created-at", request.createdAt());
        cfg.set(path + ".reviewed-at", request.reviewedAt());
        cfg.set(path + ".reviewer", request.reviewerName());
    }

    private static String newRequestId() {
        // 毫秒时间戳 + UUID 前 8 位，避免 hashCode 负数/同毫秒碰撞
        return Long.toHexString(System.currentTimeMillis()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 读取单条审核记录；UUID/状态字段损坏时返回 empty（跳过坏记录，不拖垮全表）。 */
    private Optional<ReviewRequest> readRequest(FileConfiguration cfg, String path) {
        try {
            String typeId = cfg.getString(path + ".type", "");
            if (typeId.isEmpty()) {
                return Optional.empty();
            }
            String applicantStr = cfg.getString(path + ".applicant", "");
            if (applicantStr.isEmpty()) {
                return Optional.empty();
            }
            UUID applicant = UUID.fromString(applicantStr);
            Map<String, String> data = new HashMap<>();
            ConfigurationSection dataSection = cfg.getConfigurationSection(path + ".data");
            if (dataSection != null) {
                dataSection.getKeys(false).forEach(k -> data.put(k, dataSection.getString(k)));
            }
            ReviewRequest.Status status = ReviewRequest.Status.valueOf(cfg.getString(path + ".status", "PENDING"));
            long createdAt = cfg.getLong(path + ".created-at", 0L);
            long reviewedAt = cfg.getLong(path + ".reviewed-at", 0L);
            String reviewer = cfg.getString(path + ".reviewer");
            return Optional.of(new ReviewRequest(
                    path.substring(path.lastIndexOf('.') + 1),
                    typeId,
                    applicant,
                    data,
                    status,
                    createdAt,
                    reviewedAt,
                    reviewer));
        } catch (Exception e) {
            // 坏记录（UUID/状态非法、字段缺失）跳过，避免单条损坏拖垮全部审核功能
            java.util.logging.Logger.getLogger("OrzMC.PermissionStore")
                    .warning("跳过损坏的审核记录: " + path + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    // ---- stats 时长读取（与一期同源逻辑） ----

    /** 服务器原生 stats 目录（动态解析，世界加载后有效）。 */
    static Path statsDirectory() {
        var worlds = Bukkit.getWorlds();
        if (!worlds.isEmpty()) {
            Path p = worlds.get(0).getWorldFolder().getAbsoluteFile().toPath();
            while (p != null) {
                if (Files.exists(p.resolve("players").resolve("stats"))) {
                    return p.resolve("players").resolve("stats");
                }
                p = p.getParent();
            }
        }
        return Bukkit.getWorldContainer()
                .getAbsoluteFile()
                .toPath()
                .resolve("world")
                .resolve("players")
                .resolve("stats");
    }

    /** 读取 stats 文件的 minecraft:play_time（tick），文件缺失/异常返回 0。 */
    static long readPlayTimeTicks(Path statsFile) {
        if (!Files.exists(statsFile)) {
            return 0;
        }
        try {
            var content = Files.readString(statsFile);
            var json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            var custom = json.getAsJsonObject("stats").getAsJsonObject("minecraft:custom");
            var playTime = custom == null ? null : custom.get(PLAY_TIME_KEY);
            return playTime == null ? 0 : playTime.getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }
}
