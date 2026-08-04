package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientTypeHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Type-Effektivitäts-Report als Antwort auf TypeRequestPacket. Entweder ein
 * einzelner Fehler-Marker ("H|<query>", ungültige Suche) oder strukturierte Ergebniszeilen
 * ("D|"/"Q|"/"S|"/"E|...") ohne fertig formulierten Text - der Client übersetzt alles über
 * Sprachschlüssel. Siehe TypeHelper.buildTypeResultsForGui.
 */
public record TypeResponsePacket(List<String> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TypeResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "type_response"));

    public static final StreamCodec<ByteBuf, TypeResponsePacket> CODEC =
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8)
            .map(
                (ArrayList<String> list) -> new TypeResponsePacket(list),
                (TypeResponsePacket packet) -> new ArrayList<>(packet.entries()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(TypeResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientTypeHelper.setEntries(packet.entries()));
    }
}
