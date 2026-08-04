package com.cobblecompanion.cobbledollarscreate.data;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nutzer-Vorgabe: Empfänger-Dropdown im Lagerticker-Preis-Editor soll ALLE dem Server bekannten
 * Spieler auflisten, nicht nur online Spieler. Es gibt keine öffentliche API, die den kompletten
 * GameProfileCache ausgibt (GameProfileCache.load() liefert zwar alles neu von der Platte, aber als
 * package-privaten Typ GameProfileInfo - von Mod-Code aus nicht nutzbar). Stattdessen wird
 * usercache.json direkt gelesen - exakt das Format, das GameProfileCache selbst schreibt/liest.
 */
public final class KnownPlayersHelper {

    public record NameUuid(String name, UUID uuid) {}

    private record UserCacheEntry(String name, String uuid) {}

    private static final Gson GSON = new Gson();

    private KnownPlayersHelper() {}

    public static List<NameUuid> getAllKnownPlayers(MinecraftServer server) {
        Map<UUID, String> byUuid = new LinkedHashMap<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            byUuid.put(player.getUUID(), player.getGameProfile().getName());
        }

        Path usercache = server.getWorldPath(LevelResource.ROOT).resolve("usercache.json");
        if (Files.exists(usercache)) {
            try (Reader reader = Files.newBufferedReader(usercache)) {
                UserCacheEntry[] entries = GSON.fromJson(reader, UserCacheEntry[].class);
                if (entries != null) {
                    for (UserCacheEntry entry : entries) {
                        if (entry == null || entry.name() == null || entry.uuid() == null) continue;
                        try {
                            byUuid.putIfAbsent(UUID.fromString(entry.uuid()), entry.name());
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            } catch (IOException ignored) {}
        }

        List<NameUuid> result = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : byUuid.entrySet()) {
            result.add(new NameUuid(entry.getValue(), entry.getKey()));
        }
        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }
}
