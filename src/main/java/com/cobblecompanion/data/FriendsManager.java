package com.cobblecompanion.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Serverseitige Freundesliste: Freundschaften (symmetrisch), offene Anfragen und die
 * zuletzt bekannten Spielernamen (für Anzeige von Offline-Freunden). Persistiert als
 * Gson-JSON im Weltordner, gleiches Muster wie {@link PlayerActivityTracker}.
 */
public class FriendsManager {

    /** Ergebnis von sendRequest, damit der Packet-Handler die passende Chat-Meldung wählen kann. */
    public enum RequestResult {
        SENT,
        ACCEPTED_MUTUAL,   // Gegenrichtung war schon pending -> direkt Freundschaft
        ALREADY_FRIENDS,
        ALREADY_PENDING,
        PLAYER_NOT_FOUND,
        SELF
    }

    private static class Data {
        Map<UUID, Set<UUID>> friends = new HashMap<>();
        Map<UUID, Set<UUID>> pendingRequests = new HashMap<>(); // Key = Empfänger der Anfrage
        Map<UUID, String> lastKnownNames = new HashMap<>();
        // Pro-Spieler-Einstellung "Freunde dürfen zu mir teleportieren" - serverseitig persistiert
        // (das rein clientseitige ClientSettingsHelper-Feld wird per TeleportPreferencePacket
        // hierher gespiegelt). Fehlender Eintrag = false (Default, konsistent zum Client-Default).
        Map<UUID, Boolean> allowTeleportToMe = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("cobblecompanion_friends.json");
        load();
    }

    public static void recordLogin(ServerPlayer player) {
        data.lastKnownNames.put(player.getUUID(), player.getName().getString());
        save();
    }

    /**
     * Sendet eine Freundschaftsanfrage per Spielernamen. Löst den Namen über die
     * Online-Liste und den Profil-Cache des Servers auf. Hat der Zielspieler selbst
     * schon eine Anfrage an uns offen, wird direkt die Freundschaft geschlossen.
     */
    public static RequestResult sendRequest(MinecraftServer server, ServerPlayer from, String targetName) {
        UUID targetUuid = resolveUuid(server, targetName);
        if (targetUuid == null) return RequestResult.PLAYER_NOT_FOUND;
        if (targetUuid.equals(from.getUUID())) return RequestResult.SELF;
        if (areFriends(from.getUUID(), targetUuid)) return RequestResult.ALREADY_FRIENDS;

        // Gegenrichtung schon offen? Dann direkt annehmen.
        if (getPendingFor(from.getUUID()).contains(targetUuid)) {
            accept(from.getUUID(), targetUuid);
            return RequestResult.ACCEPTED_MUTUAL;
        }

        Set<UUID> pending = data.pendingRequests.computeIfAbsent(targetUuid, k -> new HashSet<>());
        if (!pending.add(from.getUUID())) return RequestResult.ALREADY_PENDING;
        save();
        return RequestResult.SENT;
    }

    /** Nimmt die Anfrage von sender an receiver an (falls vorhanden) und schließt die Freundschaft. */
    public static boolean accept(UUID receiver, UUID sender) {
        Set<UUID> pending = data.pendingRequests.get(receiver);
        if (pending == null || !pending.remove(sender)) return false;
        data.friends.computeIfAbsent(receiver, k -> new HashSet<>()).add(sender);
        data.friends.computeIfAbsent(sender, k -> new HashSet<>()).add(receiver);
        save();
        return true;
    }

    public static boolean decline(UUID receiver, UUID sender) {
        Set<UUID> pending = data.pendingRequests.get(receiver);
        if (pending == null || !pending.remove(sender)) return false;
        save();
        return true;
    }

    /** Entfernt die Freundschaft in beide Richtungen. */
    public static boolean remove(UUID a, UUID b) {
        boolean changed = false;
        Set<UUID> aFriends = data.friends.get(a);
        if (aFriends != null) changed |= aFriends.remove(b);
        Set<UUID> bFriends = data.friends.get(b);
        if (bFriends != null) changed |= bFriends.remove(a);
        if (changed) save();
        return changed;
    }

    public static boolean areFriends(UUID a, UUID b) {
        Set<UUID> aFriends = data.friends.get(a);
        return aFriends != null && aFriends.contains(b);
    }

    public static Set<UUID> getFriends(UUID uuid) {
        return data.friends.getOrDefault(uuid, Collections.emptySet());
    }

    /** UUIDs der Spieler, deren Anfrage an diesen Spieler noch offen ist. */
    public static Set<UUID> getPendingFor(UUID uuid) {
        return data.pendingRequests.getOrDefault(uuid, Collections.emptySet());
    }

    public static String getKnownName(UUID uuid) {
        return data.lastKnownNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    public static boolean isTeleportAllowed(UUID uuid) {
        return data.allowTeleportToMe.getOrDefault(uuid, false);
    }

    public static void setTeleportAllowed(UUID uuid, boolean allowed) {
        data.allowTeleportToMe.put(uuid, allowed);
        save();
    }

    /** Öffentlicher Zugriff auf die Namensauflösung, u.a. für Chat-Commands (z.B. "accept friendrequest &lt;name&gt;"). */
    public static UUID resolvePlayerName(MinecraftServer server, String name) {
        return resolveUuid(server, name);
    }

    /** Alle Spieler, die sich jemals eingeloggt haben (UUID -> letzter bekannter Name) - für den Professor-Tab. */
    public static Map<UUID, String> getAllKnownPlayers() {
        return Collections.unmodifiableMap(data.lastKnownNames);
    }

    private static UUID resolveUuid(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        // Offline: zuerst unsere eigene Namens-Historie (case-insensitiv), dann Profil-Cache.
        for (Map.Entry<UUID, String> entry : data.lastKnownNames.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) return entry.getKey();
        }
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(name)
                .map(profile -> profile.getId())
                .orElse(null);
        }
        return null;
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<Data>(){}.getType();
            Data loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                if (loaded.friends == null) loaded.friends = new HashMap<>();
                if (loaded.pendingRequests == null) loaded.pendingRequests = new HashMap<>();
                if (loaded.lastKnownNames == null) loaded.lastKnownNames = new HashMap<>();
                if (loaded.allowTeleportToMe == null) loaded.allowTeleportToMe = new HashMap<>();
                data = loaded;
            }
        } catch (IOException ignored) {}
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
