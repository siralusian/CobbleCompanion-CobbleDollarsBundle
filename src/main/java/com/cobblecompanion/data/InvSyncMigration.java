package com.cobblecompanion.data;

import com.cobblecompanion.CobbleCompanion;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import java.util.Optional;
import java.util.UUID;

/**
 * Einmalige, automatische Übernahme der Alt-Daten aus dem InvSync-Datapack (siehe
 * GamemodeInventorySyncManager-Klassenkommentar) - läuft genau einmal beim Server-Start, danach
 * bleibt data.migrationDone dauerhaft true, egal ob etwas gefunden wurde oder nicht.
 *
 * Das Datapack speichert pro Spieler unter "invsync:player<UID>" (UID kommt aus dem Scoreboard-
 * Objective "invUID") ein CompoundTag mit "Inventories.Inventory0..3" (Index = vanilla GameType-Id:
 * 0=Survival, 1=Creative, 2=Adventure, 3=Spectator - siehe save_inv_data.mcfunction im Datapack).
 * Command-Storage-Daten sind über MinecraftServer#getCommandStorage() ganz normal über die
 * vanilla-API erreichbar (Minecraft lädt/verwaltet die Datei command_storage_invsync.dat bereits
 * selbst über den SavedData-Mechanismus) - kein manuelles NBT-Datei-Parsen nötig.
 *
 * Bestehende, bereits vom neuen System gespeicherte Inventare werden NIE überschrieben
 * (storeInventoryIfAbsent) - Migration ergänzt nur Lücken.
 */
public final class InvSyncMigration {

    private static final String NAMESPACE = "invsync";
    private static final String SCOREBOARD_OBJECTIVE = "invUID";

    private InvSyncMigration() {}

    public static void runIfNeeded(MinecraftServer server) {
        if (GamemodeInventorySyncManager.isMigrationDone()) return;

        CommandStorage commandStorage = server.getCommandStorage();
        boolean hasInvSyncData = commandStorage.keys().anyMatch(rl -> rl.getNamespace().equals(NAMESPACE));
        if (!hasInvSyncData) {
            CobbleCompanion.LOGGER.info("[CC-GamemodeInventory] Kein InvSync-Datapack-Speicher gefunden - Migration übersprungen.");
            GamemodeInventorySyncManager.setMigrationDone(true);
            return;
        }

        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(SCOREBOARD_OBJECTIVE);
        if (objective == null) {
            CobbleCompanion.LOGGER.warn(
                "[CC-GamemodeInventory] InvSync-Speicher gefunden, aber kein '{}'-Scoreboard-Objective - Migration übersprungen.",
                SCOREBOARD_OBJECTIVE);
            GamemodeInventorySyncManager.setMigrationDone(true);
            return;
        }

        int migratedPlayers = 0;
        int migratedInventories = 0;
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
            String playerName = entry.owner();
            int uid = entry.value();

            Optional<GameProfile> profile = server.getProfileCache() == null
                ? Optional.empty() : server.getProfileCache().get(playerName);
            if (profile.isEmpty()) {
                CobbleCompanion.LOGGER.warn(
                    "[CC-GamemodeInventory] Migration: konnte Spielername '{}' (invUID {}) nicht zu einer UUID auflösen - übersprungen.",
                    playerName, uid);
                continue;
            }
            UUID uuid = profile.get().getId();

            ResourceLocation storageId = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "player" + uid);
            CompoundTag playerTag = commandStorage.get(storageId);
            if (!playerTag.contains("Inventories")) continue;
            CompoundTag inventories = playerTag.getCompound("Inventories");

            boolean anyForThisPlayer = false;
            for (int gameTypeId = 0; gameTypeId <= 3; gameTypeId++) {
                String key = "Inventory" + gameTypeId;
                if (!inventories.contains(key)) continue;
                ListTag invList = inventories.getList(key, Tag.TAG_COMPOUND);
                if (invList.isEmpty()) continue;
                GameType gameType = GameType.byId(gameTypeId);
                if (GamemodeInventorySyncManager.storeInventoryIfAbsent(uuid, gameType, invList)) {
                    migratedInventories++;
                    anyForThisPlayer = true;
                }
            }
            if (anyForThisPlayer) migratedPlayers++;
        }

        GamemodeInventorySyncManager.setMigrationDone(true);
        CobbleCompanion.LOGGER.info(
            "[CC-GamemodeInventory] InvSync-Migration abgeschlossen: {} Inventare für {} Spieler übernommen. "
                + "Das alte Datapack kann jetzt manuell aus dem datapacks-Ordner entfernt werden.",
            migratedInventories, migratedPlayers);
    }
}
