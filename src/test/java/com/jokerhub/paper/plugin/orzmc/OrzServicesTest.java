package com.jokerhub.paper.plugin.orzmc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * OrzServices.assemble() 需要 MockBukkit 环境执行完整的插件装配流程，
 * 该测试已在 {@code src/integrationTest/} 中通过
 * {@code CommandAndEventIntegrationTest} 覆盖。
 *
 * <p>如需单独测试 OrzServices 的装配逻辑，请通过 MockBukkit 创建
 * ServerMock 后使用 {@code MockBukkit.load(OrzMC.class)} 加载插件。</p>
 */
@Disabled("需要 MockBukkit 环境，请参考 integrationTest 套件")
class OrzServicesTest {

    @Test
    void placeholder() {
        assertTrue(true);
    }
}
