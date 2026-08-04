package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientProfessorHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> Client: Trainerpfad-Liste (RCT) des inspizierten Spielers, siehe
 * ProfessorRctListRequestPacket. entries im Format "seriesId|title|completed".
 * targetOnline=false bedeutet: Zielspieler war offline, RCTs API erlaubt dafür keinen Zugriff -
 * entries ist dann leer, der Client zeigt stattdessen einen Hinweis.
 */
public record ProfessorRctListResponsePacket(UUID targetUuid, boolean targetOnline, List<String> entries)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfessorRctListResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "professor_rct_list_response"));

    public static final StreamCodec<ByteBuf, ProfessorRctListResponsePacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, ProfessorRctListResponsePacket::targetUuid,
        ByteBufCodecs.BOOL, ProfessorRctListResponsePacket::targetOnline,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), ProfessorRctListResponsePacket::entries,
        ProfessorRctListResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(ProfessorRctListResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientProfessorHelper.setRctData(
            packet.targetUuid(), packet.targetOnline(), packet.entries()));
    }
}
