package com.cobblecompanion.data;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.integrations.ModAvailability;
import com.cobblecompanion.integrations.curios.CuriosInventoryBridge;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Ersetzt das InvSync-Datapack: reagiert auf jeden Gamemode-Wechsel (egal ob per /gamemode,
 * Server-GUI oder Fremd-Mod - PlayerChangeGameModeEvent feuert in jedem Fall) und tauscht das
 * Hauptinventar (+ Curios, falls installiert) gegen das für den Ziel-Gamemode gespeicherte aus.
 *
 * Reihenfolge ist bewusst strikt: ERST das alte Inventar persistieren, DANN erst leeren, DANN erst
 * das neue laden. Schlägt der Persistier-Schritt fehl, bricht alles vor dem Leeren ab - der Spieler
 * behält sein aktuelles Inventar unverändert. Schlägt ausnahmsweise erst das Laden NACH dem Leeren
 * fehl (z.B. korrupte NBT), steht der Spieler zwar kurzzeitig mit leerem Inventar da, aber KEIN
 * Item geht dabei tatsächlich verloren - sowohl das alte (Schritt 1) als auch das neue Inventar
 * bleiben unverändert in der JSON-Datei gespeichert und können jederzeit nachträglich wiederhergestellt
 * werden. Das unterscheidet sich fundamental vom früheren Datapack-Bug, bei dem Items komplett und
 * dauerhaft verloren gingen.
 */
public class GamemodeInventorySyncHandler {

    @SubscribeEvent
    public void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!GamemodeInventorySyncManager.isEnabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        GameType oldMode = event.getCurrentGameMode();
        GameType newMode = event.getNewGameMode();
        if (oldMode == newMode) return;

        try {
            // 1. Persistieren (alt)
            ListTag inventoryTag = player.getInventory().save(new ListTag());
            GamemodeInventorySyncManager.storeInventory(player.getUUID(), oldMode, inventoryTag);
            ListTag curiosTag = null;
            if (ModAvailability.isCuriosAvailable()) {
                curiosTag = CuriosInventoryBridge.save(player);
                GamemodeInventorySyncManager.storeCurios(player.getUUID(), oldMode, curiosTag);
            }

            // 2. Leeren
            player.getInventory().clearContent();
            if (ModAvailability.isCuriosAvailable()) {
                CuriosInventoryBridge.clear(player);
            }

            // 3. Neues laden (falls vorhanden - sonst bleibt es leer, wie beim alten Datapack)
            ListTag newInventoryTag = GamemodeInventorySyncManager.loadInventory(player.getUUID(), newMode);
            if (newInventoryTag != null) {
                player.getInventory().load(newInventoryTag);
            }
            if (ModAvailability.isCuriosAvailable()) {
                ListTag newCuriosTag = GamemodeInventorySyncManager.loadCurios(player.getUUID(), newMode);
                if (newCuriosTag != null) {
                    CuriosInventoryBridge.load(player, newCuriosTag);
                }
            }
        } catch (Throwable t) {
            CobbleCompanion.LOGGER.error(
                "[CC-GamemodeInventory] Swap für {} ({} -> {}) fehlgeschlagen - Inventar bleibt unangetastet",
                player.getName().getString(), oldMode, newMode, t);
        }
    }
}
