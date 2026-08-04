package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.cobbledollarscreate.data.CreateStockTickerPriceManager;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Nutzer-Vorgabe: Blöcke, die AdminOp über eines unserer Create-Editoren speziell konfiguriert hat
 * (aktuell: der "Schlaue Beobachter", siehe ContentObserverConfigManager, UND ein Lagerticker mit
 * aktivierter Bezahlpflicht, siehe CreateStockTickerPriceManager), sollen von normalen Spielern
 * "wie Bedrock" nicht mehr abgebaut werden können - verhindert versehentliches/böswilliges
 * Entfernen einer mühsam eingestellten Konfiguration. Echte Minecraft-OPs bleiben ausgenommen
 * (können jederzeit selbst abbauen, z.B. um neu zu platzieren).
 *
 * Nur registriert, wenn ModAvailability.isCreateAvailable() (siehe CobbleCompanionDollarsCreate.onServerStarting).
 */
public class AdminProtectedBlockHandler {

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2)) return; // echte OPs dürfen immer abbauen

        var level = event.getLevel();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        var pos = event.getPos();

        // Bugfix (Live-Fund): CreateStockTickerPriceManager.isEnabled() beantwortet nur "verlangt
        // DIESES (bereits als Ticker bekannte) Netzwerk Bezahlung" - OHNE eigene Prüfung, ob an
        // dieser Position überhaupt ein Lagerticker steht. Ohne den folgenden
        // StockTickerBlockEntity-Check galt dadurch JEDER Block im Spiel als "geschützt".
        // Nutzer-Vorgabe: die Bezahlpflicht gilt jetzt pro NETZWERK (behaviour.freqId), nicht mehr
        // pro einzelnem Ticker-Block - siehe CreateStockTickerPriceManager-Klassenkommentar.
        boolean stockTickerProtected = serverLevel.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker
            && ticker.behaviour != null
            && CreateStockTickerPriceManager.isEnabled(ticker.behaviour.freqId);
        boolean protectedBlock = ContentObserverConfigManager.isConfigured(serverLevel.dimension(), pos)
            || stockTickerProtected;
        if (!protectedBlock) return;

        event.setCanceled(true);
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.admin_protected_block", "This block was specially configured by an admin and can't be broken."));
    }
}
