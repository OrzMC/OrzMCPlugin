package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WorldMaintenanceServiceTest extends ServiceTestBase {
    @Test
    public void testPruneOldZips() throws Exception {
        File tmp = Files.createTempDirectory("wm-prune").toFile();
        tmp.deleteOnExit();
        // create 5 zip files with different timestamps
        for (int i = 0; i < 5; i++) {
            File f = new File(tmp, "b" + i + ".zip");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(("x" + i).getBytes());
            }
            // space timestamps
            TimeUnit.MILLISECONDS.sleep(10);
        }
        Assertions.assertEquals(5, Objects.requireNonNull(tmp.listFiles((d, n) -> n.endsWith(".zip"))).length);
        WorldMaintenanceService.pruneOldZips(tmp, 2);
        File[] left = tmp.listFiles((d, n) -> n.endsWith(".zip"));
        Assertions.assertTrue(Objects.requireNonNull(left).length <= 2);
    }

    @Test
    public void formatDuration_milliseconds() {
        Assertions.assertEquals("0毫秒", WorldMaintenanceService.formatDuration(0));
        Assertions.assertEquals("854毫秒", WorldMaintenanceService.formatDuration(854));
        Assertions.assertEquals("999毫秒", WorldMaintenanceService.formatDuration(999));
        // 负值按 0 处理
        Assertions.assertEquals("0毫秒", WorldMaintenanceService.formatDuration(-5));
        Assertions.assertEquals("0毫秒", WorldMaintenanceService.formatDuration(-1_000));
    }

    @Test
    public void formatDuration_seconds() {
        Assertions.assertEquals("1秒", WorldMaintenanceService.formatDuration(1000));
        // 四舍五入到秒
        Assertions.assertEquals("2秒", WorldMaintenanceService.formatDuration(1500));
        Assertions.assertEquals("59秒", WorldMaintenanceService.formatDuration(59_000));
        // 59.6 秒四舍五入进位为 1 分
        Assertions.assertEquals("1分", WorldMaintenanceService.formatDuration(59_600));
    }

    @Test
    public void formatDuration_minutes() {
        Assertions.assertEquals("1分", WorldMaintenanceService.formatDuration(60_000));
        // 老板示例：154901ms ≈ 2分35秒
        Assertions.assertEquals("2分35秒", WorldMaintenanceService.formatDuration(154_901));
        Assertions.assertEquals("59分59秒", WorldMaintenanceService.formatDuration(3_599_000));
    }

    @Test
    public void formatDuration_hours() {
        Assertions.assertEquals("1小时", WorldMaintenanceService.formatDuration(3_600_000));
        Assertions.assertEquals("1小时1分1秒", WorldMaintenanceService.formatDuration(3_661_000));
        Assertions.assertEquals("2小时3分", WorldMaintenanceService.formatDuration(7_380_000));
        // 小时>0、分钟=0、秒>0 → 补 0 分占位
        Assertions.assertEquals("1小时0分5秒", WorldMaintenanceService.formatDuration(3_605_000));
        // 59分59.5 秒四舍五入进位到整小时
        Assertions.assertEquals("1小时", WorldMaintenanceService.formatDuration(3_599_500));
    }
}
