package com.jokerhub.paper.plugin.orzmc.infra.templates;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TemplateRendererTest {
    @Test
    public void testRenderSimple() {
        String tpl = "{name} 上线\n世界:{world} 坐标:{x},{y},{z}\n角色:{role}\n在线:{online_count}/{max_count}\n{online_list}";
        String out = TemplateRenderer.render(
                tpl,
                Map.of(
                        "name", "Steve",
                        "world", "world",
                        "x", "1",
                        "y", "64",
                        "z", "1",
                        "role", "admin",
                        "online_count", "3",
                        "max_count", "20",
                        "online_list", "Alex\nBob"));
        String expected = "Steve 上线\n世界:world 坐标:1,64,1\n角色:admin\n在线:3/20\nAlex\nBob";
        Assertions.assertEquals(expected, out);
    }
}
