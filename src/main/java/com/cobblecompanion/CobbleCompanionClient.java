package com.cobblecompanion;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = CobbleCompanion.MOD_ID, dist = Dist.CLIENT)
public class CobbleCompanionClient {

    public CobbleCompanionClient(IEventBus modEventBus) {
        CobbleCompanion.LOGGER.info("CobbleCompanion client loaded.");
    }
}