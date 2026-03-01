package com.jokerhub.paper.plugin.orzmc.features.security;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * 玩家认证服务
 * 统一处理玩家登录状态检查，支持内置在线状态检查和第三方插件认证检查
 */
public final class PlayerAuthenticationService {
    private static final List<String> LOGINSECURITY_PACKAGES =
            Arrays.asList("com.github.games647.loginsecurity", "com.lenis0012.bukkit.loginsecurity");

    // 缓存反射结果
    private Class<?> loginSecurityClass;
    private Method getInstanceMethod;
    private Method getSessionManagerMethod;
    private Class<?> sessionManagerClass;
    private Method getPlayerSessionMethod;
    private Class<?> playerSessionClass;
    private Method isLoggedInMethod;
    private Method isAuthenticatedMethod;

    public PlayerAuthenticationService() {
        initializeReflection();
    }

    /**
     * 初始化反射缓存
     */
    private void initializeReflection() {
        for (String pkg : LOGINSECURITY_PACKAGES) {
            try {
                // 尝试第一种API方式 (isAuthenticated)
                try {
                    loginSecurityClass = Class.forName(pkg + ".LoginSecurity");
                    getInstanceMethod = loginSecurityClass.getMethod("getInstance");
                    getSessionManagerMethod = loginSecurityClass.getMethod("getSessionManager");
                    sessionManagerClass = Class.forName(pkg + ".session.SessionManager");
                    isAuthenticatedMethod = sessionManagerClass.getMethod("isAuthenticated", Player.class);
                    return;
                } catch (NoSuchMethodException e) {
                    // 尝试第二种API方式 (getPlayerSession + isLoggedIn)
                    loginSecurityClass = Class.forName(pkg + ".LoginSecurity");
                    getInstanceMethod = loginSecurityClass.getMethod("getInstance");
                    getSessionManagerMethod = loginSecurityClass.getMethod("getSessionManager");
                    sessionManagerClass = Class.forName(pkg + ".session.SessionManager");
                    getPlayerSessionMethod = sessionManagerClass.getMethod("getPlayerSession", Player.class);
                    playerSessionClass = Class.forName(pkg + ".session.PlayerSession");
                    isLoggedInMethod = playerSessionClass.getMethod("isLoggedIn");
                    return;
                }
            } catch (Exception e) {
                // 尝试下一个包名
            }
        }
    }

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
    }

    /**
     * 检查玩家是否通过LoginSecurity认证
     *
     * @param player 玩家对象
     * @return 是否已认证
     */
    private boolean isLoginSecurityAuthenticated(Player player) {
        try {
            if (loginSecurityClass == null) {
                // LoginSecurity未安装，默认认为已认证
                return true;
            }

            Object loginSecurityInstance = getInstanceMethod.invoke(null);
            Object sessionManager = getSessionManagerMethod.invoke(loginSecurityInstance);

            if (isAuthenticatedMethod != null) {
                // 使用isAuthenticated方法
                return (boolean) isAuthenticatedMethod.invoke(sessionManager, player);
            } else if (getPlayerSessionMethod != null && isLoggedInMethod != null) {
                // 使用getPlayerSession + isLoggedIn方法
                Object playerSession = getPlayerSessionMethod.invoke(sessionManager, player);
                return (boolean) isLoggedInMethod.invoke(playerSession);
            }

            // 无法获取认证状态，默认认为已认证
            return true;
        } catch (Exception e) {
            // 认证检查失败，默认认为已认证
            return true;
        }
    }
}
