package com.cobblecompanion.client.data;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Zentrale Prüfung, ob ein CobbleCompanion-Payload gerade an den Server gesendet werden darf
 * (Live-Crash-Fund: ClientEventHandler.onClientTick sendete Ctrl/Alt-Tastenstatus-Pakete
 * bedingungslos, NeoForge wirft dann eine UnsupportedOperationException und crasht den Client,
 * wenn der verbundene Server dieses Payload nicht kennt - siehe ClientEventHandler-
 * Klassenkommentar). registrar.optional() bei der Registrierung reicht dafür NICHT aus (siehe
 * CobbleCompanion.hasClientChannel()-Kommentar, das serverseitige Gegenstück dieser Prüfung für
 * S2C-Pakete) - jeder einzelne Kanal wird pro Verbindung UNABHÄNGIG von den anderen ausgehandelt,
 * ein "ein Kanal da -> alle da"-Schluss ist also falsch. Deshalb hier keine einmalige globale
 * Prüfung, sondern eine, die JEDES Payload einzeln per NetworkRegistry.hasChannel() gegen die
 * aktuelle Verbindung prüft.
 */
public final class ClientNetworkUtil {

    private ClientNetworkUtil() {}

    /** true, wenn eine Verbindung besteht UND der Server DIESES Payload für sie ausgehandelt hat. */
    public static boolean canSendToServer(ResourceLocation payloadId) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && NetworkRegistry.hasChannel(connection, payloadId);
    }

    // Verhindert Chat-Spam bei wiederholten Klicks auf blockierte Aktionen - merkt sich die
    // Verbindung, für die zuletzt gewarnt wurde, und warnt pro Verbindung nur einmal.
    private static ClientPacketListener lastWarnedConnection = null;

    /** Wie canSendToServer(), zeigt aber beim ERSTEN Fehlschlag pro Verbindung einen Chat-Hinweis. */
    public static boolean canSendToServerOrWarn(ResourceLocation payloadId) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && NetworkRegistry.hasChannel(connection, payloadId)) {
            return true;
        }
        if (connection != lastWarnedConnection) {
            lastWarnedConnection = connection;
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("cobblecompanion.msg.server_feature_unavailable"), false);
            }
        }
        return false;
    }
}
