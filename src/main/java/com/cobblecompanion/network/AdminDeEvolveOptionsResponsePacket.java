package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> Client: Antwort auf AdminDeEvolveOptionsRequestPacket - toSpeciesId leer = keine Vorentwicklung (Basis-Form). */
public record AdminDeEvolveOptionsResponsePacket(String toSpeciesId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminDeEvolveOptionsResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_deevolve_options_response"));

    public static final StreamCodec<ByteBuf, AdminDeEvolveOptionsResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, AdminDeEvolveOptionsResponsePacket::toSpeciesId,
        AdminDeEvolveOptionsResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminDeEvolveOptionsResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.cobblecompanion.client.data.ClientProfessorHelper.setDeEvolveOption(packet.toSpeciesId());
        });
    }
}
