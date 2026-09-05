package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

/**
 * QQ 出站网关 WS 地址解析（builtin QQ adapter，方案 §6 / EasyBot gateway.rs 同构）。
 *
 * <p>每次建连（含自动重连）解析一次网关地址；调用方（{@link QqGatewayClient}）持有当前 access_token 传入。
 * 实现按 EasyBot 语义分类结果：token 被平台拒绝（HTTP 401 / body 11244/11242）视为 {@link Status#AUTH}
 * （应由调用方触发令牌强制重换），其余非 2xx / 网络 / 解析失败为 {@link Status#TRANSIENT}（退避重试安全）。</p>
 */
@FunctionalInterface
public interface QqGatewayUrlFetcher {

    /** 解析网关 WS 地址。 */
    Result fetch(String accessToken);

    enum Status {
        /** 成功拿到 WS 地址。 */
        SUCCESS,
        /** 临时性失败（网络/服务端错误/解析失败），可退避重试。 */
        TRANSIENT,
        /** token 被拒（401/11244/11242），需要强制重换令牌。 */
        AUTH
    }

    /** 解析结果：{@link Status#SUCCESS} 时 {@code url} 有效，其余情况 {@code url} 为 null。 */
    record Result(Status status, String url) {
        public static Result success(String url) {
            return new Result(Status.SUCCESS, url);
        }

        public static Result transientFailure() {
            return new Result(Status.TRANSIENT, null);
        }

        public static Result authFailure() {
            return new Result(Status.AUTH, null);
        }
    }
}
