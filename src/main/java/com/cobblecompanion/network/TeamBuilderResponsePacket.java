package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientTeamBuilderHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server -> Client: Antwort auf TeamBuilderRequestPacket, siehe ClientTeamBuilderHelper. */
public record TeamBuilderResponsePacket(List<String> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TeamBuilderResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "team_builder_response"));

    public static final StreamCodec<ByteBuf, TeamBuilderResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), TeamBuilderResponsePacket::entries,
        TeamBuilderResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TeamBuilderResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientTeamBuilderHelper.setResult(packet.entries()));
    }
}
