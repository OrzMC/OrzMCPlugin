package com.jokerhub.paper.plugin.orzmc.infra.config;

/**
 * 模板事件键常量。
 *
 * <p>集中管理所有 {@link TypedConfigProvider#renderEvent(String, java.util.Map)} 和
 * {@link com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier#event(String, com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope)}
 * 中使用的 key，避免散落各处的魔数字符串。</p>
 */
public final class TemplateKeys {

    private TemplateKeys() {}

    // ---- 玩家事件 ----
    public static final String PLAYER_JOIN = "player_join";
    public static final String PLAYER_KICK = "player_kick";
    public static final String PLAYER_QUIT = "player_quit";
    public static final String PLAYER_DIGEST = "player_digest";

    // ---- 命令事件 ----
    public static final String COMMAND_OUTPUT = "command_output";
    public static final String COMMAND_HELP = "command_help";
    public static final String COMMAND_PLAYERS = "command_players";
    public static final String COMMAND_WHITELIST_HEADER = "command_whitelist_header";
    public static final String COMMAND_WHITELIST_PAGE = "command_whitelist_page";
    public static final String COMMAND_WHITELIST_CLEANUP = "command_whitelist_cleanup";
    public static final String COMMAND_WHITELIST_ADD_RESULT = "command_whitelist_add_result";
    public static final String COMMAND_WHITELIST_REMOVE_RESULT = "command_whitelist_remove_result";
    public static final String COMMAND_ADMIN_REQUIRED = "command_admin_required";
    public static final String COMMAND_USAGE = "command_usage";
    public static final String COMMAND_BACKUP = "command_backup";
    public static final String COMMAND_OPTIMIZE = "command_optimize";
    public static final String COMMAND_OPTIMIZE_DISABLED = "command_optimize_disabled";
    public static final String COMMAND_BLACKLIST_LIST = "command_blacklist_list";
    public static final String COMMAND_BLACKLIST_ADD = "command_blacklist_add";
    public static final String COMMAND_BLACKLIST_REMOVE = "command_blacklist_remove";
    public static final String COMMAND_BLACKLIST_ERROR = "command_blacklist_error";

    // ---- 安全事件 ----
    public static final String GEOIP_BLOCK = "geoip_block";
    public static final String WHITELIST_BLOCK = "whitelist_block";
    public static final String WHITELIST_TOGGLE_ALERT = "whitelist_toggle_alert";
    public static final String COMMAND_GUARD_BLOCKED = "command_guard_blocked";
    public static final String SECURITY_AUDIT = "security_audit";
    public static final String LOGIN_RATE_LIMIT_ALERT = "login_rate_limit_alert";
    public static final String EXPLOIT_BLOCKED = "exploit_blocked";
    public static final String IP_BLACKLIST_BLOCK = "ip_blacklist_block";

    // ---- TNT 事件 ----
    public static final String TNT_ALERT = "tnt_alert";

    // ---- 服务端事件 ----
    public static final String SERVER_LOAD = "server_load";
    public static final String SERVER_STOP = "server_stop";

    // ---- 维护事件 ----
    public static final String MAINTENANCE_BACKUP_STAGE = "maintenance_backup_stage";
    public static final String MAINTENANCE_BACKUP_DONE = "maintenance_backup_done";
    public static final String MAINTENANCE_BACKUP_ERROR = "maintenance_backup_error";
    public static final String MAINTENANCE_OPTIMIZE_STAGE = "maintenance_optimize_stage";
    public static final String MAINTENANCE_OPTIMIZE_DONE = "maintenance_optimize_done";
    public static final String MAINTENANCE_OPTIMIZE_ERROR = "maintenance_optimize_error";

    // ---- 权限审核事件（通用审核框架）----
    public static final String REVIEW_SUBMITTED = "review_submitted";
    public static final String REVIEW_CANCELLED = "review_cancelled";
    public static final String REVIEW_APPROVED = "review_approved";
    public static final String REVIEW_REJECTED = "review_rejected";
    public static final String RANK_PROMOTED = "rank_promoted";
    public static final String RANK_DEMOTED = "rank_demoted";
    public static final String COMMAND_RANK_STATUS = "rank_status";
    public static final String COMMAND_REVIEW_LIST = "command_review_list";
    public static final String COMMAND_REVIEW_LIST_EMPTY = "command_review_list_empty";
    public static final String COMMAND_REVIEW_RESULT = "command_review_result";
    public static final String COMMAND_REVIEW_ERROR = "command_review_error";

    // ---- 异常事件 ----
    public static final String EXCEPTION_ALERT = "exception_alert";

    // ---- 其他 ----
    public static final String HELP = "help";
    public static final String MESSAGE = "message";
    public static final String PATTERNS = "patterns";
    public static final String MOTD = "motd";

    /**
     * 所有已知的模板事件 key。用于 {@link ConfigHealthCheck} 校验。
     *
     * <p>不含 {@link #PLAYER_DIGEST}、{@link #COMMAND_GUARD_BLOCKED}、{@link #SECURITY_AUDIT}、
     * {@link #LOGIN_RATE_LIMIT_ALERT}、{@link #EXPLOIT_BLOCKED} 与 {@link #IP_BLACKLIST_BLOCK}：
     * 调用方始终传入 fallback 兜底文案（PLAYER_DIGEST 在 {@code Templates} 中有完整 Java 默认模板，
     * COMMAND_GUARD_BLOCKED 在 {@code CommandGuardEventService}、SECURITY_AUDIT 在
     * {@code StartupSecurityAuditService}、LOGIN_RATE_LIMIT_ALERT 在 {@code LoginRateLimitEventService}、
     * EXPLOIT_BLOCKED 在 {@code ExploitHardeningEventService}、IP_BLACKLIST_BLOCK 在
     * {@code OrzPlayerEvent} 中内联兜底），升级安装（templates.yml 已存在故未复制新默认值）不携带
     * 该键即可正常工作；纳入 ALL 会要求模板文件提供该键，造成升级后每次启动的持久「缺失」告警。</p>
     */
    public static final String[] ALL = {
        PLAYER_JOIN,
        PLAYER_KICK,
        PLAYER_QUIT,
        COMMAND_OUTPUT,
        COMMAND_HELP,
        COMMAND_PLAYERS,
        COMMAND_WHITELIST_HEADER,
        COMMAND_WHITELIST_PAGE,
        COMMAND_WHITELIST_CLEANUP,
        COMMAND_WHITELIST_ADD_RESULT,
        COMMAND_WHITELIST_REMOVE_RESULT,
        COMMAND_ADMIN_REQUIRED,
        COMMAND_USAGE,
        COMMAND_BACKUP,
        COMMAND_OPTIMIZE,
        COMMAND_OPTIMIZE_DISABLED,
        COMMAND_BLACKLIST_LIST,
        COMMAND_BLACKLIST_ADD,
        COMMAND_BLACKLIST_REMOVE,
        COMMAND_BLACKLIST_ERROR,
        GEOIP_BLOCK,
        WHITELIST_BLOCK,
        WHITELIST_TOGGLE_ALERT,
        TNT_ALERT,
        SERVER_LOAD,
        SERVER_STOP,
        MAINTENANCE_BACKUP_STAGE,
        MAINTENANCE_BACKUP_DONE,
        MAINTENANCE_BACKUP_ERROR,
        MAINTENANCE_OPTIMIZE_STAGE,
        MAINTENANCE_OPTIMIZE_DONE,
        MAINTENANCE_OPTIMIZE_ERROR,
        REVIEW_SUBMITTED,
        REVIEW_CANCELLED,
        REVIEW_APPROVED,
        REVIEW_REJECTED,
        RANK_PROMOTED,
        RANK_DEMOTED,
        COMMAND_RANK_STATUS,
        COMMAND_REVIEW_LIST,
        COMMAND_REVIEW_LIST_EMPTY,
        COMMAND_REVIEW_RESULT,
        COMMAND_REVIEW_ERROR,
        EXCEPTION_ALERT,
    };

    /** 命令模板 key 子集（用于 i18n 校验）。 */
    public static final String[] COMMAND_KEYS = {
        COMMAND_OUTPUT,
        COMMAND_HELP,
        COMMAND_PLAYERS,
        COMMAND_WHITELIST_HEADER,
        COMMAND_WHITELIST_PAGE,
        COMMAND_WHITELIST_CLEANUP,
        COMMAND_WHITELIST_ADD_RESULT,
        COMMAND_WHITELIST_REMOVE_RESULT,
        COMMAND_ADMIN_REQUIRED,
        COMMAND_USAGE,
        COMMAND_BACKUP,
        COMMAND_OPTIMIZE,
        COMMAND_OPTIMIZE_DISABLED,
        COMMAND_BLACKLIST_LIST,
        COMMAND_BLACKLIST_ADD,
        COMMAND_BLACKLIST_REMOVE,
        COMMAND_BLACKLIST_ERROR,
    };
}
