package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientProfessorHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: PC-Boxen (boxNames + pcEntries mit "cc_box"/"cc_slot"-Feldern in jedem Tag)
 * + Team des inspizierten Spielers, siehe ProfessorPCRequestPacket. Der Client baut daraus echte
 * ClientPC/ClientParty-Objekte und öffnet Cobblemons eigenes PCGUI im Nur-Lese-Modus.
 */
public record ProfessorPCResponsePacket(String targetName, List<String> boxNames,
                                         List<CompoundTag> pcEntries, List<CompoundTag> partyEntries)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfessorPCResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "professor_pc_response"));

    public static final StreamCodec<ByteBuf, ProfessorPCResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ProfessorPCResponsePacket::targetName,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), ProfessorPCResponsePacket::boxNames,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.COMPOUND_TAG), ProfessorPCResponsePacket::pcEntries,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.COMPOUND_TAG), ProfessorPCResponsePacket::partyEntries,
        ProfessorPCResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(ProfessorPCResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientProfessorHelper.setPCData(
            packet.targetName(), packet.boxNames(), packet.pcEntries(), packet.partyEntries()));
    }
}
