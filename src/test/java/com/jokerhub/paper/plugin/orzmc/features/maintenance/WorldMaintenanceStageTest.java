package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import com.jokerhub.orzmc.world.ProgressStage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WorldMaintenanceStageTest {
    @Test
    public void testStageMapping() {
        Assertions.assertEquals(
                WorldMaintenanceService.MaintenanceStage.Done,
                WorldMaintenanceService.mapProgressStage(ProgressStage.Done));
        Assertions.assertEquals(
                WorldMaintenanceService.MaintenanceStage.Running, WorldMaintenanceService.mapProgressStage(null));
    }
}
