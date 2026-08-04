package com.cobblecompanion.data;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;

/**
 * Nutzer-Vorgabe (Punkt 3 CobbleDollars-Wunschliste): setzt CommandWhitelistManager tatsächlich
 * durch. Vanilla verweigert Nicht-OP-Spielern schon beim PARSEN eines zu hoch berechtigten Befehls
 * (Brigadiers CommandNode.requires()-Prüfung greift bereits während dispatcher.parse(), nicht erst
 * bei execute()) - das ORIGINALE ParseResults (siehe CommandEvent.getParseResults()) enthält für
 * einen solchen Befehl deshalb KEINEN passenden Knoten mehr, egal was wir hier tun. Der einzige Weg,
 * einen freigeschalteten Befehl trotzdem auszuführen: den rohen Befehlstext MIT einer künstlich
 * angehobenen CommandSourceStack (siehe withPermission) neu parsen und selbst ausführen, das
 * ursprüngliche (unprivilegierte) Event dabei abbrechen, damit es nicht zusätzlich fehlschlägt.
 *
 * Nur registriert, wenn mindestens ein Muster konfiguriert ist wäre unnötige Mikro-Optimierung -
 * der Event-Feuert-Overhead pro Tastendruck-Befehl ist vernachlässigbar, deshalb immer registriert
 * (siehe CobbleCompanion-Konstruktor, unconditional wie CreativeRestrictionHandler).
 */
public class CommandWhitelistHandler {

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (source.hasPermission(2)) return; // schon genug Rechte, keine Freischaltung nötig
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        if (!CreativeRestrictionHandler.isRestricted(player)) return;

        String command = event.getParseResults().getReader().getString();
        if (!CommandWhitelistManager.isAllowed(command)) return;

        MinecraftServer server = source.getServer();
        if (server == null) return;

        event.setCanceled(true);
        CommandSourceStack elevated = source.withPermission(4); // Nutzer-Vorgabe: immer volles OP
        ParseResults<CommandSourceStack> reparsed = server.getCommands().getDispatcher().parse(command, elevated);
        server.getCommands().performCommand(reparsed, command);
    }
}
