package com.jokerhub.paper.plugin.orzmc.infra.i18n;

/**
 * 消息 key 常量（镜像 {@code TemplateKeys} 做法，禁散落字面量）。
 *
 * <p>值必须与语言包（messages/messages_zh-CN.yml 为完整性基线）中的真实 key 一致；
 * 跨语言 key 集/占位符一致性由 {@link I18nHealth} 与一致性单测守护。</p>
 */
public final class MessageKeys {

    private MessageKeys() {}

    // ---- common.*：跨渠道通用反馈（游戏内命令拦截器/通用提示） ----
    public static final String COMMON_COOLDOWN = "common.cooldown";
    public static final String COMMON_ADMIN_REQUIRED = "common.admin_required";
    public static final String COMMON_PLAYER_REQUIRED = "common.player_required";
    public static final String COMMON_PRISON_DENIED = "common.prison_denied";
    public static final String COMMON_UNKNOWN_PLAYER = "common.unknown_player";
    public static final String COMMON_COPY_COORDS = "common.copy_coords";

    // ---- prison.*：坐牢（P2f1） ----
    public static final String PRISON_LP_UNAVAILABLE = "prison.lp_unavailable";
    public static final String PRISON_LP_UNAVAILABLE_RELEASE = "prison.lp_unavailable_release";
    public static final String PRISON_IMPRISON_FAILED = "prison.imprison_failed";
    public static final String PRISON_RELEASE_FAILED = "prison.release_failed";
    public static final String PRISON_NOT_PRISONER = "prison.not_prisoner";
    public static final String PRISON_IMPRISON_PLAYER_MSG = "prison.imprison_player_msg";
    public static final String PRISON_RELEASE_PLAYER_MSG = "prison.release_player_msg";
    public static final String PRISON_IMPRISON_OK = "prison.imprison_ok";
    public static final String PRISON_RELEASE_OK = "prison.release_ok";
    public static final String PRISON_PLAYER_NOT_FOUND = "prison.player_not_found";

    // ---- gamemode.*：自动切生存提示（P2f1） ----
    public static final String GAMEMODE_FIX_MESSAGE = "gamemode.fix_message";

    // ---- tnt 广播前缀（P2f1 样式收口） ----
    public static final String TNT_PREFIX_TNT = "tnt.prefix_tnt";
    public static final String TNT_PREFIX_EXPLOSION = "tnt.prefix_explosion";

    // ---- review.*：/apply 命令文案（P2f2） ----
    public static final String REVIEW_LIST_HEADER = "review.list_header";
    public static final String REVIEW_NO_TYPES = "review.no_types";
    public static final String REVIEW_SUMMARY_PREFIX = "review.summary_prefix";
    public static final String REVIEW_REASON_SEP = "review.reason_sep";
    public static final String REVIEW_TYPE_UNKNOWN = "review.type_unknown";
    public static final String REVIEW_TYPE_UNKNOWN_BARE = "review.type_unknown_bare";
    public static final String REVIEW_MY_APPLICATIONS = "review.my_applications";
    public static final String REVIEW_NO_APPLICATIONS = "review.no_applications";
    public static final String REVIEW_STATUS_PENDING = "review.status_pending";
    public static final String REVIEW_STATUS_APPROVED = "review.status_approved";
    public static final String REVIEW_STATUS_REJECTED = "review.status_rejected";
    public static final String REVIEW_STATUS_CANCELLED = "review.status_cancelled";
    public static final String REVIEW_REVIEWER_SUFFIX = "review.reviewer_suffix";

    // ---- review.* 业务流（P2f2b；审核业务结果统一默认语言 R1，保框架纯无 Bukkit 依赖） ----
    public static final String REVIEW_NOT_ELIGIBLE = "review.not_eligible";
    public static final String REVIEW_ALREADY_PENDING = "review.already_pending";
    public static final String REVIEW_SUBMITTED_NOTIFY = "review.submitted_notify";
    public static final String REVIEW_SUBMITTED_OK = "review.submitted_ok";
    public static final String REVIEW_PROCESSING_CANCEL = "review.processing_cancel";
    public static final String REVIEW_NOT_FOUND = "review.not_found";
    public static final String REVIEW_OWN_CANCEL_ONLY = "review.own_cancel_only";
    public static final String REVIEW_ALREADY_PROCESSED_CANCEL = "review.already_processed_cancel";
    public static final String REVIEW_CANCELLED_NOTIFY = "review.cancelled_notify";
    public static final String REVIEW_CANCELLED_OK = "review.cancelled_ok";
    public static final String REVIEW_PROCESSING_REVIEW = "review.processing_review";
    public static final String REVIEW_ALREADY_PROCESSED_REVIEW = "review.already_processed_review";
    public static final String REVIEW_UNKNOWN_TYPE_REVIEW = "review.unknown_type_review";
    public static final String REVIEW_AUTH_FAILED = "review.auth_failed";
    public static final String REVIEW_AUTH_FAILED_TOP = "review.auth_failed_top";
    public static final String REVIEW_STATE_CHANGED = "review.state_changed";
    public static final String REVIEW_STATE_SAVE_FAILED = "review.state_save_failed";
    public static final String REVIEW_PENDING_NONE_SELF = "review.pending_none_self";
    public static final String REVIEW_PLAYER_NOT_FOUND = "review.player_not_found";
    public static final String REVIEW_NO_PENDING_APPLICANT = "review.no_pending_applicant";
    public static final String REVIEW_MULTIPLE_PENDING = "review.multiple_pending";
    public static final String REVIEW_APPROVED_NOTIFY = "review.approved_notify";
    public static final String REVIEW_REJECTED_NOTIFY = "review.rejected_notify";
    public static final String REVIEW_APPROVED_OK = "review.approved_ok";
    public static final String REVIEW_REJECTED_OK = "review.rejected_ok";
    public static final String REVIEW_REASON_ARG = "review.reason_arg";

    // ---- rank.*：/rank 状态 / 升降级提示 / 权限组名（P2f3） ----
    public static final String RANK_HEADER_CURRENT = "rank.header_current";
    public static final String RANK_TIMELINE_WITH_PROGRESS = "rank.timeline_with_progress";
    public static final String RANK_TIMELINE_DONE = "rank.timeline_done";
    public static final String RANK_TIMELINE_PLAIN = "rank.timeline_plain";
    public static final String RANK_PROGRESS_MET = "rank.progress_met";
    public static final String RANK_PROGRESS_LEFT = "rank.progress_left";
    public static final String RANK_NEXT_AUTO = "rank.next_auto";
    public static final String RANK_NEXT_APPLY = "rank.next_apply";
    public static final String RANK_TOP_LEVEL = "rank.top_level";
    public static final String RANK_UNKNOWN_GROUP = "rank.unknown_group";
    public static final String RANK_NO_APPLICABLE = "rank.no_applicable";
    public static final String RANK_TYPE_ENTRY = "rank.type_entry";
    public static final String RANK_LIST_SEP = "rank.list_sep";
    public static final String RANK_PROMOTE_NOTIFY = "rank.promote_notify";
    public static final String RANK_DEMOTE_NOTIFY = "rank.demote_notify";
    public static final String RANK_GROUP_PREFIX = "rank.group.";
    public static final String RANK_GROUP_ADMIN = "rank.group.admin";
    public static final String RANK_GROUP_BUILDER = "rank.group.builder";
    public static final String RANK_GROUP_MEMBER = "rank.group.member";
    public static final String RANK_GROUP_DEFAULT = "rank.group.default";

    // ---- maintenance.cmd.*：/maintenance 状态（P2g1） ----
    public static final String MAINTENANCE_CMD_BUSY_MANUAL_ENTER = "maintenance.cmd.busy_manual_enter";
    public static final String MAINTENANCE_CMD_ALREADY_MANUAL = "maintenance.cmd.already_manual";
    public static final String MAINTENANCE_CMD_NOT_ACTIVE = "maintenance.cmd.not_active";
    public static final String MAINTENANCE_CMD_BUSY_EXIT = "maintenance.cmd.busy_exit";
    public static final String MAINTENANCE_CMD_OFF = "maintenance.cmd.off";
    public static final String MAINTENANCE_CMD_REASON_BACKUP = "maintenance.cmd.reason_backup";
    public static final String MAINTENANCE_CMD_REASON_OPTIMIZE = "maintenance.cmd.reason_optimize";
    public static final String MAINTENANCE_CMD_REASON_MANUAL = "maintenance.cmd.reason_manual";

    // ---- guide / menu（P2h1） ----
    public static final String GUIDE_NOT_CONFIGURED = "guide.not_configured";
    public static final String GUIDE_GOT = "guide.got";
    public static final String MENU_WIP = "menu.wip";

    // ---- teleport.bow.*：传送弓（P2a） ----
    public static final String TELEPORT_BOW_NAME = "teleport.bow.name";
    public static final String TELEPORT_BOW_TAG = "teleport.bow.tag";
    public static final String TELEPORT_BOW_LORE = "teleport.bow.lore";
    public static final String TELEPORT_BOW_GIVEN = "teleport.bow.given";
    public static final String TELEPORT_BOW_DISABLED = "teleport.bow.disabled";
    public static final String TELEPORT_BOW_HIT_WATER = "teleport.bow.hit_water";
    public static final String TELEPORT_BOW_HIT_LAVA = "teleport.bow.hit_lava";
    public static final String TELEPORT_BOW_CROSS_WORLD = "teleport.bow.cross_world";
    public static final String TELEPORT_BOW_BAD_HEIGHT = "teleport.bow.bad_height";
    public static final String TELEPORT_BOW_NO_LANDING = "teleport.bow.no_landing";
    public static final String TELEPORT_BOW_DONE = "teleport.bow.done";

    // ---- whitelist.*：登录踢出提示/群通知（P2b） ----
    public static final String WHITELIST_KICK_JOIN_HINT_PREFIX = "whitelist.kick.join_hint_prefix";
    public static final String WHITELIST_KICK_JOIN_HINT_SUFFIX = "whitelist.kick.join_hint_suffix";
    public static final String WHITELIST_KICK_DISCORD_JOIN = "whitelist.kick.discord_join";
    public static final String WHITELIST_NOTIFY_BLOCKED = "whitelist.notify.blocked";
    public static final String WHITELIST_NOTIFY_TOGGLE_OFF = "whitelist.notify.toggle_off";

    // ---- portal.*：/portal 命令反馈（P2c） ----
    public static final String PORTAL_USAGE = "portal.usage";
    public static final String PORTAL_USAGE_REMOVE = "portal.usage_remove";
    public static final String PORTAL_PORT_REQUIRED = "portal.port_required";
    public static final String PORTAL_NOT_FOUND = "portal.not_found";
    public static final String PORTAL_REMOVED = "portal.removed";
    public static final String PORTAL_CREATED = "portal.created";
    public static final String PORTAL_COMMAND_DESC = "portal.command_desc";

    // ---- tnt.*：TNT 防护提示/告警标签（P2c） ----
    public static final String TNT_ANCHOR_DISABLED = "tnt.anchor_disabled";
    public static final String TNT_PLACE_COOLDOWN = "tnt.place_cooldown";
    public static final String TNT_COOLDOWN_SECONDS = "tnt.cooldown_seconds";
    public static final String TNT_PLACE_DISABLED = "tnt.place_disabled";
    public static final String TNT_PRIME_DENIED = "tnt.prime_denied";
    public static final String TNT_PRIME = "tnt.prime";
    public static final String TNT_DISPENSE_FORBIDDEN = "tnt.dispense_forbidden";
    public static final String TNT_EXPLODE = "tnt.explode";
    public static final String TNT_BLOCK_EXPLODE = "tnt.block_explode";
    public static final String TNT_PLACEMENT_MSG = "tnt.placement_msg";
    public static final String TNT_AT = "tnt.at";
    public static final String TNT_PLACED = "tnt.placed";

    // ---- player.notify.*：上下线聚合摘要版块标签（P2d） ----
    public static final String PLAYER_NOTIFY_JOIN_LABEL = "player.notify.join_label";
    public static final String PLAYER_NOTIFY_QUIT_LABEL = "player.notify.quit_label";
    public static final String PLAYER_NOTIFY_KICK_LABEL = "player.notify.kick_label";
    public static final String PLAYER_NOTIFY_HIDDEN_MORE = "player.notify.hidden_more";

    // ---- geoip.*：地区白名单提示/告警（P2d） ----
    public static final String GEOIP_KICK_REGION = "geoip.kick_region";
    public static final String GEOIP_KICK_UNVERIFIABLE = "geoip.kick_unverifiable";
    public static final String GEOIP_OUTCOME_ALLOWED_FAILOPEN = "geoip.outcome_allowed_failopen";
    public static final String GEOIP_OUTCOME_DENIED_FAILCLOSE = "geoip.outcome_denied_failclose";
    public static final String GEOIP_OUTCOME_ALLOWED = "geoip.outcome_allowed";
    public static final String GEOIP_OUTCOME_DENIED = "geoip.outcome_denied";
    public static final String GEOIP_ALERT_EXCEPTION = "geoip.alert_exception";
    public static final String GEOIP_ALERT_LOOKUP_FAILED = "geoip.alert_lookup_failed";
    public static final String GEOIP_ALERT_TIMEOUT = "geoip.alert_timeout";

    // ---- login.*：登录访问控制（P2d2；pre-login 无客户端 locale → 默认语言） ----
    public static final String LOGIN_UNKNOWN_PLAYER = "login.unknown_player";
    public static final String LOGIN_IP_BANNED = "login.ip_banned";
    public static final String LOGIN_NAME_RULE_DENIED = "login.name_rule_denied";

    // ---- guard.*：危险命令拦截（P2e；安全提示默认语言 R1） ----
    public static final String GUARD_DENY_REASON = "guard.deny_reason";
    public static final String GUARD_SELECTOR_WARN = "guard.selector_warn";
    public static final String GUARD_SOURCE_PLAYER = "guard.source_player";
    public static final String GUARD_SOURCE_CONSOLE = "guard.source_console";

    // ---- exploit.*：漏洞利用加固（P2e） ----
    public static final String EXPLOIT_NOTIFY_PAGES = "exploit.notify_pages";
    public static final String EXPLOIT_NOTIFY_ATTRIBUTES = "exploit.notify_attributes";

    // ---- ratelimit.*：登录限流（P2e） ----
    public static final String RATELIMIT_REASON_FREQUENCY = "ratelimit.reason_frequency";
    public static final String RATELIMIT_REASON_CONCURRENT = "ratelimit.reason_concurrent";

    // ---- serverlife.*：服务端生命周期/维护 MOTD 外壳 var 值词汇（P5-2，R1） ----
    public static final String SERVERLIFE_MODE_ONLINE = "serverlife.mode_online";
    public static final String SERVERLIFE_MODE_OFFLINE = "serverlife.mode_offline";
    public static final String SERVERLIFE_STATUS_STARTUP = "serverlife.status_startup";
    public static final String SERVERLIFE_STATUS_RELOAD = "serverlife.status_reload";
    public static final String SERVERLIFE_MOTD_TITLE = "serverlife.motd_title";
    public static final String SERVERLIFE_QQ_LABEL = "serverlife.qq_label";
    public static final String SERVERLIFE_DISCORD_LABEL = "serverlife.discord_label";
    public static final String SERVERLIFE_DISCORD_HOVER = "serverlife.discord_hover";

    // ---- audit.*：启动安全自检 var 值词汇（P5-2，R1） ----
    public static final String AUDIT_ONLINE_ON = "audit.online_on";
    public static final String AUDIT_ONLINE_OFF = "audit.online_off";
    public static final String AUDIT_COMMAND_BLOCK_ON = "audit.command_block_on";
    public static final String AUDIT_COMMAND_BLOCK_OFF = "audit.command_block_off";
    public static final String AUDIT_RCON_OFF = "audit.rcon_off";
    public static final String AUDIT_RCON_ON = "audit.rcon_on";
    public static final String AUDIT_WHITELIST_OFF = "audit.whitelist_off";
    public static final String AUDIT_WHITELIST_ENFORCED = "audit.whitelist_enforced";
    public static final String AUDIT_WHITELIST_NOT_ENFORCED = "audit.whitelist_not_enforced";
    public static final String AUDIT_OPS_ZERO = "audit.ops_zero";
    public static final String AUDIT_OPS_COUNT = "audit.ops_count";
    public static final String AUDIT_OPS_LIST = "audit.ops_list";
    public static final String AUDIT_PLUGINS_NONE = "audit.plugins_none";

    // ---- cmd.*（游戏内命令帮助/提示，P6。desc 注册期 default_lang；错误提示按 sender） ----
    public static final String CMD_DESC_APPLY = "cmd.desc.apply";
    public static final String CMD_DESC_REVIEW = "cmd.desc.review";
    public static final String CMD_REVIEW_FAILED = "cmd.review_failed";
    public static final String CMD_DESC_GUIDE = "cmd.desc.guide";
    public static final String CMD_DESC_MENU = "cmd.desc.menu";
    public static final String CMD_DESC_TPBOW = "cmd.desc.tpbow";
    public static final String CMD_DESC_BOT = "cmd.desc.bot";
    public static final String CMD_DESC_ORZDEBUG = "cmd.desc.orzdebug";
    public static final String CMD_DESC_MAINTENANCE = "cmd.desc.maintenance";
    public static final String CMD_ORZDEBUG_ACCEPTED = "cmd.orzdebug_accepted";
    public static final String CMD_ORZDEBUG_USAGE = "cmd.orzdebug_usage";
    public static final String CMD_MAINTENANCE_ON = "cmd.maintenance_on";
    public static final String CMD_MAINTENANCE_OFF = "cmd.maintenance_off";
    public static final String CMD_MAINTENANCE_USAGE = "cmd.maintenance_usage";
    public static final String CMD_DESC_BLACKLIST = "cmd.desc.blacklist";
    public static final String CMD_DESC_RANK = "cmd.desc.rank";
    public static final String CMD_DESC_PRISON = "cmd.desc.prison";
    public static final String CMD_DESC_UPDATE = "cmd.desc.update";
    public static final String CMD_RANK_PLAYER_NOT_FOUND = "cmd.rank_player_not_found";
    public static final String CMD_PRISON_USAGE = "cmd.prison_usage";
    public static final String CMD_PRISON_FAILED = "cmd.prison_failed";
    public static final String CMD_UPDATE_USAGE = "cmd.update_usage";
    public static final String CMD_UPDATE_CHECK_FAILED_REASON = "cmd.update_check_failed_reason";
    public static final String CMD_UPDATE_CHECK_FAILED = "cmd.update_check_failed";
    public static final String CMD_UPDATE_UNKNOWN_LOCAL = "cmd.update_unknown_local";
    public static final String CMD_UPDATE_AVAILABLE = "cmd.update_available";
    public static final String CMD_UPDATE_UP_TO_DATE = "cmd.update_up_to_date";
    public static final String CMD_UPDATE_NO_CHANNEL_INFO = "cmd.update_no_channel_info";
    public static final String CMD_UPDATE_DOWNLOADED = "cmd.update_downloaded";
    public static final String CMD_UPDATE_ALREADY_DOWNLOADED = "cmd.update_already_downloaded";
    public static final String CMD_UPDATE_NO_UPDATE = "cmd.update_no_update";
    public static final String CMD_UPDATE_BUSY = "cmd.update_busy";
    public static final String CMD_UPDATE_DOWNLOAD_FAILED = "cmd.update_download_failed";
    public static final String CMD_UPDATE_DOWNLOAD_FAILED_REASON = "cmd.update_download_failed_reason";
    public static final String CMD_DESC_CONFIG = "cmd.desc.config";
    public static final String CMD_CONFIG_IM_USAGE = "cmd.config_im_usage";
    public static final String CMD_CONFIG_IM_BIND_USAGE = "cmd.config_im_bind_usage";
    public static final String CMD_CONFIG_IM_TEST_USAGE = "cmd.config_im_test_usage";
    public static final String CMD_CONFIG_IM_BIND_WRITE_FAILED = "cmd.config_im_bind_write_failed";
    public static final String CMD_CONFIG_IM_BIND_OK = "cmd.config_im_bind_ok";
    public static final String CMD_CONFIG_IM_BACKEND_NOT_BUILTIN = "cmd.config_im_backend_not_builtin";
    public static final String CMD_CONFIG_IM_TEST_EMPTY = "cmd.config_im_test_empty";
    public static final String CMD_CONFIG_IM_NO_PLATFORM = "cmd.config_im_no_platform";
    public static final String CMD_CONFIG_IM_TEST_SENT = "cmd.config_im_test_sent";
    public static final String CMD_CONFIG_IM_ERR_PLATFORM_EMPTY = "cmd.config_im_err_platform_empty";
    public static final String CMD_CONFIG_IM_ERR_PLATFORM_INVALID = "cmd.config_im_err_platform_invalid";
    public static final String CMD_CONFIG_IM_ERR_CHAT_TYPE = "cmd.config_im_err_chat_type";
    public static final String CMD_CONFIG_IM_ERR_CHAT_ID = "cmd.config_im_err_chat_id";
    public static final String CMD_CONFIG_IM_ERR_ROLE = "cmd.config_im_err_role";
    public static final String CMD_CONSOLE_OP_ONLY = "cmd.console_op_only";
    public static final String CMD_CONFIG_IM_PANEL_TITLE = "cmd.config_im_panel_title";
    public static final String CMD_CONFIG_IM_PANEL_BACKEND = "cmd.config_im_panel_backend";
    public static final String CMD_CONFIG_IM_PANEL_QQ_PLATFORM = "cmd.config_im_panel_qq_platform";
    public static final String CMD_CONFIG_IM_PANEL_CONNECTION = "cmd.config_im_panel_connection";
    public static final String CMD_CONFIG_IM_PANEL_EASY_BOT = "cmd.config_im_panel_easy_bot";
    public static final String CMD_CONFIG_IM_PANEL_BINDINGS_TITLE = "cmd.config_im_panel_bindings_title";
    public static final String CMD_CONFIG_IM_PANEL_NO_BINDINGS = "cmd.config_im_panel_no_bindings";
    public static final String CMD_CONFIG_IM_PANEL_CANDIDATES_TITLE = "cmd.config_im_panel_candidates_title";
    public static final String CMD_CONFIG_IM_PANEL_NONE = "cmd.config_im_panel_none";
    public static final String CMD_CONFIG_IM_PANEL_ROLE_LEGEND = "cmd.config_im_panel_role_legend";
    public static final String CMD_CONFIG_IM_SETUP_TITLE = "cmd.config_im_setup_title";
    public static final String CMD_CONFIG_IM_SETUP_1 = "cmd.config_im_setup_1";
    public static final String CMD_CONFIG_IM_SETUP_2 = "cmd.config_im_setup_2";
    public static final String CMD_CONFIG_IM_SETUP_3 = "cmd.config_im_setup_3";
    public static final String CMD_CONFIG_IM_SETUP_4 = "cmd.config_im_setup_4";
    public static final String CMD_CONFIG_IM_SETUP_5 = "cmd.config_im_setup_5";
    public static final String CMD_CONFIG_IM_STATE_BUILTIN_NO_PLATFORM = "cmd.config_im_state.builtin_no_platform";
    public static final String CMD_CONFIG_IM_STATE_QQ_USABLE = "cmd.config_im_state.qq_usable";
    public static final String CMD_CONFIG_IM_STATE_QQ_MISSING = "cmd.config_im_state.qq_missing";
    public static final String CMD_CONFIG_IM_STATE_CONNECTED = "cmd.config_im_state.connected";
    public static final String CMD_CONFIG_IM_STATE_DISCONNECTED = "cmd.config_im_state.disconnected";
    public static final String CMD_CONFIG_IM_STATE_SETUP_READY = "cmd.config_im_state.setup_ready";
    public static final String CMD_CONFIG_IM_STATE_SETUP_MISSING = "cmd.config_im_state.setup_missing";
    public static final String CMD_CONFIG_IM_STATE_DISABLED_SUFFIX = "cmd.config_im_state.disabled_suffix";
    public static final String CMD_CONFIG_USAGE_TITLE = "cmd.config.usage_title";
    public static final String CMD_CONFIG_USAGE_LIST = "cmd.config.usage.list";
    public static final String CMD_CONFIG_USAGE_GET = "cmd.config.usage.get";
    public static final String CMD_CONFIG_USAGE_SET = "cmd.config.usage.set";
    public static final String CMD_CONFIG_USAGE_RESET = "cmd.config.usage.reset";
    public static final String CMD_CONFIG_USAGE_DUMP = "cmd.config.usage.dump";
    public static final String CMD_CONFIG_USAGE_RELOAD = "cmd.config.usage.reload";
    public static final String CMD_CONFIG_USAGE_EXAMPLE = "cmd.config.usage.example";
    public static final String CMD_CONFIG_GET_USAGE = "cmd.config.get_usage";
    public static final String CMD_CONFIG_SET_USAGE = "cmd.config.set_usage";
    public static final String CMD_CONFIG_RESET_USAGE = "cmd.config.reset_usage";
    public static final String CMD_CONFIG_LIST_TITLE = "cmd.config.list_title";
    public static final String CMD_CONFIG_UNKNOWN_PATH = "cmd.config.unknown_path";
    public static final String CMD_CONFIG_FIELD_TYPE = "cmd.config.field_type";
    public static final String CMD_CONFIG_FIELD_DEFAULT = "cmd.config.field_default";
    public static final String CMD_CONFIG_FIELD_FILE = "cmd.config.field_file";
    public static final String CMD_CONFIG_FIELD_DESC = "cmd.config.field_desc";
    public static final String CMD_CONFIG_VALUE_EMPTY = "cmd.config.value_empty";
    public static final String CMD_CONFIG_CFG_NOT_LOADED = "cmd.config.cfg_not_loaded";
    public static final String CMD_CONFIG_SET_OK = "cmd.config.set_ok";
    public static final String CMD_CONFIG_TYPE_ERROR = "cmd.config.type_error";
    public static final String CMD_CONFIG_INVALID_VALUE = "cmd.config.invalid_value";
    public static final String CMD_CONFIG_RESET_OK = "cmd.config.reset_ok";
    public static final String CMD_CONFIG_RELOAD_OK = "cmd.config.reload_ok";
    public static final String CMD_CONFIG_RELOAD_NOT_FOUND = "cmd.config.reload_not_found";
    public static final String CMD_CONFIG_RELOAD_ALL_OK = "cmd.config.reload_all_ok";
    public static final String CMD_CONFIG_DUMP_TITLE = "cmd.config.dump_title";
    public static final String CMD_CONFIG_DUMP_DEFAULT = "cmd.config.dump_default";
    public static final String BOTSTATUS_STATE_ENABLED = "botstatus.state.enabled";
    public static final String BOTSTATUS_STATE_DISABLED = "botstatus.state.disabled";
    public static final String BOTSTATUS_STATE_WS_OK = "botstatus.state.ws_ok";
    public static final String BOTSTATUS_STATE_WS_NOT_OK = "botstatus.state.ws_not_ok";
    public static final String BOTSTATUS_STATE_HTTP_OK = "botstatus.state.http_ok";
    public static final String BOTSTATUS_STATE_HTTP_UNKNOWN = "botstatus.state.http_unknown";
    public static final String BOTSTATUS_STATE_HTTP_NOT_OK = "botstatus.state.http_not_ok";
    public static final String BOTSTATUS_STATE_CONNECTED = "botstatus.state.connected";
    public static final String BOTSTATUS_STATE_DISCONNECTED = "botstatus.state.disconnected";
    public static final String BOTSTATUS_STATE_NOT_CHECKED = "botstatus.state.not_checked";
    public static final String BOTSTATUS_STATE_HEALTHY = "botstatus.state.healthy";
    public static final String BOTSTATUS_STATE_UNHEALTHY = "botstatus.state.unhealthy";
    public static final String BOTSTATUS_FAILED_PLATFORMS = "botstatus.failed_platforms";
    public static final String BOTSTATUS_FAILED_PLATFORMS_RATIO = "botstatus.failed_platforms_ratio";
    public static final String BOTSTATUS_HOVER_DETAILS = "botstatus.hover_details";
    public static final String BOTSTATUS_ERROR_PREFIX = "botstatus.error_prefix";
    public static final String BOT_E_TRUNCATED = "bot.e.truncated";
    public static final String BOT_E_EXEC_OK = "bot.e.exec_ok";
    public static final String BOT_E_EXEC_NOT_FOUND = "bot.e.exec_not_found";
}
