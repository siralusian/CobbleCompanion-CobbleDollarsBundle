package com.cobblecompanion.cobbledollars.network;

import com.cobblecompanion.cobbledollars.CobbleCompanionDollars;
import com.cobblecompanion.cobbledollars.client.ClientCobbleDollarsHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: aktueller Cobbledollars-Kontostand des lokalen Spielers. balance als String
 * (BigInteger.toString()) übertragen - kein eingebauter StreamCodec für BigInteger, der
 * Wertebereich passt problemlos in einen normalen UTF8-String.
 */
public record CobbleDollarsBalanceSyncPacket(String balance) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CobbleDollarsBalanceSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollars.MOD_ID, "cobbledollars_balance_sync"));

    public static final StreamCodec<ByteBuf, CobbleDollarsBalanceSyncPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(CobbleDollarsBalanceSyncPacket::new, CobbleDollarsBalanceSyncPacket::balance);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle in CobbleCompanion: Basis (RuntimeDistCleaner).
    public static void handle(CobbleDollarsBalanceSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientCobbleDollarsHelper.setBalance(packet.balance()));
    }
}
