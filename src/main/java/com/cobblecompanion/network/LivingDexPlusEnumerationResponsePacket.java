package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Antwort auf LivingDexPlusEnumerationRequestPacket. entries-Einträge im
 * Format "kategorieId|artName|formName|dexNummer" (formName leer bei der Standardform).
 */
public record LivingDexPlusEnumerationResponsePacket(List<String> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LivingDexPlusEnumerationResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "living_dex_plus_enum_response"));

    public static final StreamCodec<ByteBuf, LivingDexPlusEnumerationResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), LivingDexPlusEnumerationResponsePacket::entries,
        LivingDexPlusEnumerationResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LivingDexPlusEnumerationResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                com.cobblecompanion.client.data.LivingDexPlusHelper.setCatalog(packet.entries());
            } catch (Exception e) {
                CobbleCompanion.LOGGER.error("[CC] Fehler beim Verarbeiten des Living-Dex+-Katalogs", e);
            }
        });
    }
}
