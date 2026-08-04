package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientDimensionGamemodeHelper;
import com.cobblecompanion.data.DimensionGamemodeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server -> Client: aktuelle Dimension-Gamemode-Regeln ("dim=mode"-Einträge, siehe
 * DimensionGamemodeManager) + alle aktuell geladenen Dimensions-IDs (für den Listeneditor in
 * Settings > Gamemodes - Auswahl per Cycle-Button statt freiem Text, siehe CompanionScreen-
 * Kommentar zur Nutzer-Vorgabe). Nur an AdminOp-Spieler gesendet (siehe sendTo).
 */
public record DimensionGamemodeSyncPacket(List<String> ruleEntries, List<String> availableDimensions)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DimensionGamemodeSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "dimension_gamemode_sync"));

    public static final StreamCodec<ByteBuf, DimensionGamemodeSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), DimensionGamemodeSyncPacket::ruleEntries,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), DimensionGamemodeSyncPacket::availableDimensions,
        DimensionGamemodeSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Baut das Paket aus dem aktuellen Server-Stand und schickt es an player - Aufrufer: Login (AdminOp) + nach jeder Regeländerung (Command/GUI-Paket). */
    public static void sendTo(ServerPlayer player) {
        if (player.getServer() == null) return;
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, String> e : DimensionGamemodeManager.getAllRules().entrySet()) {
            entries.add(e.getKey() + "=" + e.getValue());
        }
        List<String> dimensions = new ArrayList<>();
        for (ResourceKey<Level> key : player.getServer().levelKeys()) {
            dimensions.add(key.location().toString());
        }
        PacketDistributor.sendToPlayer(player, new DimensionGamemodeSyncPacket(entries, dimensions));
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(DimensionGamemodeSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDimensionGamemodeHelper.setStatus(packet.ruleEntries(), packet.availableDimensions()));
    }
}
