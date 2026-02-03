package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WorldMaintenanceServiceTest {
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
}
