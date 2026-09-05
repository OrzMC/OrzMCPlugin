package com.jokerhub.paper.plugin.orzmc.infra.bot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 未绑定会话自动发现（方案 D11）：builtin 平台收到未绑定会话消息时记录候选，
 * 供控制台日志与 /config im status 候选列表提示（不向陌生会话回消息）。
 *
 * <p>候选提示直接给出<b>可复制执行的完整 bind 命令</b>（{@link #bindCommands(String)}，按会话类型
 * 给角色建议：群 → admin_group/player_group，私聊 → admin_dm），管理员无需再自行拼接指令。</p>
 *
 * <p>绑定成功后调用 {@link #clear(String)} 移除候选。线程安全（WS 回调线程写入、命令线程读取）。</p>
 */
public final class ImDiscoveryCandidates {

    /** 候选容量上限：防止陌生会话轰炸刷爆内存（超限丢弃最旧语义退化为直接丢弃新记录）。 */
    private static final int MAX_CANDIDATES = 256;

    private final ConcurrentMap<String, Long> seen = new ConcurrentHashMap<>(); // target → lastSeenMs

    /** 记录一条未绑定会话候选。 */
    public void record(String target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        if (seen.size() >= MAX_CANDIDATES) {
            seen.clear(); // 粗粒度保护：候选仅提示用途，丢旧保新
        }
        seen.put(target, System.currentTimeMillis());
    }

    /** 绑定成功后移除该会话候选。 */
    public void clear(String target) {
        if (target != null) {
            seen.remove(target);
        }
    }

    /** 当前候选快照（按最近出现倒序），供 status 展示。 */
    public List<Candidate> snapshot() {
        List<Candidate> list = new ArrayList<>();
        seen.forEach((target, lastSeenMs) -> list.add(new Candidate(target, lastSeenMs)));
        list.sort(Comparator.comparingLong(Candidate::lastSeenMs).reversed());
        return list;
    }

    public boolean isEmpty() {
        return seen.isEmpty();
    }

    /** 一条候选：目标会话 + 最近出现时间（毫秒）。 */
    public record Candidate(String target, long lastSeenMs) {

        /** 会话类型（group/user；从 target 反解，不可解析返回 null）。 */
        public String chatType() {
            int i1 = target.indexOf(':');
            if (i1 <= 0 || i1 == target.length() - 1) {
                return null;
            }
            int i2 = target.indexOf(':', i1 + 1);
            if (i2 <= i1 + 1) {
                return null;
            }
            return target.substring(i1 + 1, i2);
        }

        /** 平台会话 id（从 target 反解；不可解析返回 null）。 */
        public String chatId() {
            int i1 = target.indexOf(':');
            if (i1 < 0) {
                return null;
            }
            int i2 = target.indexOf(':', i1 + 1);
            if (i2 < 0 || i2 == target.length() - 1) {
                return null;
            }
            return target.substring(i2 + 1);
        }
    }

    /**
     * 候选 target → 可复制执行的完整 bind 命令列表（命令行纯净，可直接整行复制执行）：
     * 群会话给 admin_group/player_group 两条建议，私聊给 admin_dm 一条；target 不可解析返回空。
     * 角色含义（admin_group=管理群 / player_group=玩家群 / admin_dm=管理员私聊）由展示区标题说明。
     */
    public static List<String> bindCommands(String target) {
        if (target == null) {
            return List.of();
        }
        int i1 = target.indexOf(':');
        if (i1 <= 0 || i1 == target.length() - 1) {
            return List.of();
        }
        int i2 = target.indexOf(':', i1 + 1);
        if (i2 <= i1 + 1 || i2 == target.length() - 1) {
            return List.of();
        }
        String platform = target.substring(0, i1);
        String chatType = target.substring(i1 + 1, i2);
        String chatId = target.substring(i2 + 1);
        List<String> cmds = new ArrayList<>();
        if ("group".equals(chatType)) {
            cmds.add("/config im bind " + platform + " group " + chatId + " admin_group");
            cmds.add("/config im bind " + platform + " group " + chatId + " player_group");
        } else if ("user".equals(chatType)) {
            cmds.add("/config im bind " + platform + " user " + chatId + " admin_dm");
        }
        return cmds;
    }
}
