package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientProfessorHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Spielerliste für den Professor-Tab ("uuid|name|online" je Zeile, siehe
 * ProfessorHelper.getAllPlayers).
 */
public record ProfessorPlayerListResponsePacket(List<String> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfessorPlayerListResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "professor_player_list_response"));

    public static final StreamCodec<ByteBuf, ProfessorPlayerListResponsePacket> CODEC =
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8)
            .map(
                (ArrayList<String> list) -> new ProfessorPlayerListResponsePacket(list),
                (ProfessorPlayerListResponsePacket packet) -> new ArrayList<>(packet.entries()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(ProfessorPlayerListResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientProfessorHelper.setPlayers(packet.entries()));
    }
}
