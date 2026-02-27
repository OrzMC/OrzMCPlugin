package com.jokerhub.paper.plugin.orzmc.features.security;

import org.bukkit.entity.Player;

/**
 * 玩家认证服务
 * 统一处理玩家登录状态检查，支持内置在线状态检查和第三方插件认证检查
 */
public final class PlayerAuthenticationService {

    /**
     * 检查玩家是否已完全认证
     *
     * @param player 玩家对象
     * @return 是否已认证
     */
    public boolean isAuthenticated(Player player) {
        // 检查玩家是否在线
        if (player == null || !player.isOnline()) {
            return false;
        }

        // 检查LoginSecurity登录状态
        return isLoginSecurityAuthenticated(player);

        // 可以在这里添加其他认证检查逻辑
    }

    /**
     * 检查玩家是否通过LoginSecurity认证
     *
     * @param player 玩家对象
     * @return 是否已认证
     */
    private boolean isLoginSecurityAuthenticated(Player player) {
        try {
            // 尝试通过反射获取LoginSecurity的API实例
            String loginsecurityGroup = "com.lenis0012.bukkit";
            Class<?> loginSecurityClass = Class.forName(loginsecurityGroup + ".loginsecurity.LoginSecurity");
            Object loginSecurityInstance =
                    loginSecurityClass.getMethod("getInstance").invoke(null);

            // 检查玩家是否已登录
            Class<?> sessionManagerClass = Class.forName(loginsecurityGroup + ".loginsecurity.session.SessionManager");
            Object sessionManager =
                    loginSecurityClass.getMethod("getSessionManager").invoke(loginSecurityInstance);

            // 检查会话是否已认证
            return (boolean) sessionManagerClass
                    .getMethod("isAuthenticated", Player.class)
                    .invoke(sessionManager, player);
        } catch (Exception e) {
            // LoginSecurity未安装或API调用失败，默认认为已认证
            return true;
        }
    }
}
