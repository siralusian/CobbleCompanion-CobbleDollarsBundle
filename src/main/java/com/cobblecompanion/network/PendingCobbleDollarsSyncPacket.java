package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.PendingCobbleDollarsManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: eigener ausstehender Cobbledollars-Betrag (siehe PendingCobbleDollarsManager -
 * entsteht, wenn der Schlaue Beobachter einem offline Spieler etwas gutschreiben/abziehen wollte)
 * für die Home-Tab-Anzeige. amount kann negativ sein (offene Schuld).
 */
public record PendingCobbleDollarsSyncPacket(long amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PendingCobbleDollarsSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "pending_cobbledollars_sync"));

    public static final StreamCodec<ByteBuf, PendingCobbleDollarsSyncPacket> CODEC =
        ByteBufCodecs.VAR_LONG.map(PendingCobbleDollarsSyncPacket::new, PendingCobbleDollarsSyncPacket::amount);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new PendingCobbleDollarsSyncPacket(PendingCobbleDollarsManager.getPending(player.getUUID())));
    }

    public static void handle(PendingCobbleDollarsSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
            com.cobblecompanion.client.data.ClientPendingCobbleDollarsHelper.set(packet.amount()));
    }
}
