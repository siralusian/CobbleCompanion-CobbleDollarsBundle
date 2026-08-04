package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Anfrage nach frischen Living-Dex-Daten, gesendet beim
 * Wechsel auf den Living-Dex-Tab (statt nur einmalig beim Login).
 */
public record LivingDexRequestPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LivingDexRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "living_dex_request"));

    public static final StreamCodec<ByteBuf, LivingDexRequestPacket> CODEC =
        StreamCodec.unit(new LivingDexRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LivingDexRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                CobbleCompanion.sendLivingDexData(player);
            }
        });
    }
}
