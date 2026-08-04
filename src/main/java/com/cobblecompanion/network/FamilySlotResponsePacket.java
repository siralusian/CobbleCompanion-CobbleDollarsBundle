package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> Client: Antwort auf FamilySlotRequestPacket. anchorDexNumber -1, falls nicht auflösbar. */
public record FamilySlotResponsePacket(String speciesName, int anchorDexNumber) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FamilySlotResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "family_slot_response"));

    public static final StreamCodec<ByteBuf, FamilySlotResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, FamilySlotResponsePacket::speciesName,
        ByteBufCodecs.VAR_INT, FamilySlotResponsePacket::anchorDexNumber,
        FamilySlotResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FamilySlotResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.cobblecompanion.client.data.PCSortHelper.setFamilyAnchor(packet.speciesName(), packet.anchorDexNumber());
        });
    }
}
