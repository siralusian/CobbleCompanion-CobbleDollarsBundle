package com.cobblecompanion.cobbledollarscreate;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Prüft alle ~1 Sekunde auf abgelaufene Schlauer-Beobachter-Gruppen-Versprechen (siehe
 * ContentObserverPromiseManager) - gleiches Muster wie MerchantShopPeriodicSyncHandler.
 */
public class ContentObserverPromiseTickHandler {

    private static final int INTERVAL_TICKS = 20; // ~1 Sekunde

    private int counter = 0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        counter++;
        if (counter < INTERVAL_TICKS) return;
        counter = 0;

        ContentObserverPromiseManager.tick(event.getServer());
    }
}
