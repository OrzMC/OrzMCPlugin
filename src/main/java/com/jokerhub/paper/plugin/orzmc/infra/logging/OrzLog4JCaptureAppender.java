package com.jokerhub.paper.plugin.orzmc.infra.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Log4J Appender：把服务器日志行（含插件 logger 与命令输出）喂给 {@link LogCaptureService}。
 *
 * <p>在 Paper 上 ConsoleCommandSender 的 sendMessage 最终都会进入日志系统，因此挂一个
 * root Appender 即可覆盖同步 + 异步命令输出（比只包装 ConsoleCommandSender 的同步捕获
 * 覆盖面广得多）。Appender 常驻，由 {@code $e} 命令用时间窗取增量，注册/注销见
 * {@code PlatformModule}。
 */
public final class OrzLog4JCaptureAppender extends AbstractAppender {

    private final LogCaptureService captureService;

    public OrzLog4JCaptureAppender(LogCaptureService captureService) {
        // ignoreExceptions=true：append 异常不回流日志系统（避免递归/污染），捕获失败仅丢行
        super("OrzMC-LogCapture", null, null, true, Property.EMPTY_ARRAY);
        this.captureService = captureService;
    }

    @Override
    public void append(LogEvent event) {
        if (event == null || event.getMessage() == null) {
            return;
        }
        captureService.capture(event.getMessage().getFormattedMessage());
    }
}
