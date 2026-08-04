package com.cobblecompanion.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serverseitige Datenaufbereitung für den Professor-Tab (Op/AdminOp-Feature). Nutzt
 * FriendsManager.getAllKnownPlayers() als bereits vorhandene "wer hat sich je eingeloggt"-Liste,
 * statt eine eigene Registry zu pflegen.
 */
public class ProfessorHelper {

    /** "uuid|name|online" je Zeile, online zuerst, danach alphabetisch nach Name. */
    public static List<String> getAllPlayers(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : FriendsManager.getAllKnownPlayers().entrySet()) {
            UUID uuid = entry.getKey();
            String name = entry.getValue();
            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            lines.add(uuid + "|" + name + "|" + (online != null));
        }
        lines.sort((a, b) -> {
            String[] pa = a.split("\\|", -1);
            String[] pb = b.split("\\|", -1);
            boolean onlineA = Boolean.parseBoolean(pa[2]);
            boolean onlineB = Boolean.parseBoolean(pb[2]);
            if (onlineA != onlineB) return onlineA ? -1 : 1;
            return pa[1].compareToIgnoreCase(pb[1]);
        });
        return lines;
    }
}
