package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientGamemodeInventoryHelper;
import com.cobblecompanion.data.GamemodeInventorySyncManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: aktueller Ein/Aus-Status der Gamemode-Inventar-Trennung (siehe
 * GamemodeInventorySyncManager) für den Settings-Toggle (Gamemodes-Kategorie) - nur an AdminOp
 * gesendet, rein lesend (Bearbeitung per GamemodeInventorySyncTogglePacket oder Befehl).
 */
public record GamemodeInventorySyncStatusPacket(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GamemodeInventorySyncStatusPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "gamemode_inventory_sync_status"));

    public static final StreamCodec<ByteBuf, GamemodeInventorySyncStatusPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, GamemodeInventorySyncStatusPacket::enabled,
        GamemodeInventorySyncStatusPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new GamemodeInventorySyncStatusPacket(GamemodeInventorySyncManager.isEnabled()));
    }

    public static void handle(GamemodeInventorySyncStatusPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientGamemodeInventoryHelper.setEnabled(packet.enabled()));
    }
}
