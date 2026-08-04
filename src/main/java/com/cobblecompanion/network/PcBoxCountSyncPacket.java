package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblemon.mod.common.Cobblemon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: aktuelle Cobblemon-PC-Box-Anzahl (Cobblemon.INSTANCE.getConfig().
 * getDefaultBoxCount()) + ob der empfangende Spieler sie ändern darf (canEdit = OP). Gesendet bei
 * Login und nach jeder Änderung - exakt dasselbe Muster wie ServerRulesSyncPacket.
 */
public record PcBoxCountSyncPacket(int boxCount, boolean canEdit) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PcBoxCountSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "pc_box_count_sync"));

    public static final StreamCodec<ByteBuf, PcBoxCountSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PcBoxCountSyncPacket::boxCount,
        ByteBufCodecs.BOOL, PcBoxCountSyncPacket::canEdit,
        PcBoxCountSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new PcBoxCountSyncPacket(
            Cobblemon.INSTANCE.getConfig().getDefaultBoxCount(),
            com.cobblecompanion.data.AdminPermissionManager.isAdminOp(player.getUUID())));
    }

    public static void handle(PcBoxCountSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.cobblecompanion.client.data.ClientPcBoxCountHelper.apply(
            packet.boxCount(), packet.canEdit()));
    }
}
