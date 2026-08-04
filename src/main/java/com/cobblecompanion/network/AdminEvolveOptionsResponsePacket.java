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

/** Server -> Client: Antwort auf AdminEvolveOptionsRequestPacket ("toSpeciesId|toAspects" je Eintrag). */
public record AdminEvolveOptionsResponsePacket(List<String> options) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminEvolveOptionsResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_evolve_options_response"));

    public static final StreamCodec<ByteBuf, AdminEvolveOptionsResponsePacket> CODEC =
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8)
            .map(
                (ArrayList<String> list) -> new AdminEvolveOptionsResponsePacket(list),
                (AdminEvolveOptionsResponsePacket packet) -> new ArrayList<>(packet.options()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminEvolveOptionsResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.cobblecompanion.client.data.ClientProfessorHelper.setEvolveOptions(packet.options());
        });
    }
}
