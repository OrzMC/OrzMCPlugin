package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import com.jokerhub.orzmc.world.ProgressStage;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WorldMaintenanceStageTest extends ServiceTestBase {
    @Test
    public void testStageMapping() {
        Assertions.assertEquals(
                WorldMaintenanceService.MaintenanceStage.Done,
                WorldMaintenanceService.mapProgressStage(ProgressStage.Done));
        Assertions.assertEquals(
                WorldMaintenanceService.MaintenanceStage.Running, WorldMaintenanceService.mapProgressStage(null));
    }
}
