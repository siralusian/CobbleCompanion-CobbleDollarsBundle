package com.cobblecompanion.cobbledollars.network;

import com.cobblecompanion.cobbledollars.CobbleCompanionDollars;
import com.cobblecompanion.integrations.ModAvailability;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsBridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Wallet-Tab wurde geöffnet - fordert den aktuellen Cobbledollars-Kontostand
 * an. Nur relevant, wenn ModAvailability.isCobbleDollarsAvailable() - der Tab erscheint
 * clientseitig nur dann, hier zusätzlich serverseitig abgesichert.
 */
public record CobbleDollarsBalanceRequestPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CobbleDollarsBalanceRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollars.MOD_ID, "cobbledollars_balance_request"));

    public static final StreamCodec<ByteBuf, CobbleDollarsBalanceRequestPacket> CODEC =
        StreamCodec.unit(new CobbleDollarsBalanceRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CobbleDollarsBalanceRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!ModAvailability.isCobbleDollarsAvailable()) return;
            PacketDistributor.sendToPlayer(player, new CobbleDollarsBalanceSyncPacket(
                CobbleDollarsBridge.getBalance(player).toString()));
        });
    }
}
