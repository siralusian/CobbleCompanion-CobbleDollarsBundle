package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientWhoNeedsHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: eigene abgebbare Duplikate ("speciesName|count" je Eintrag) als Antwort
 * auf MyDuplicatesRequestPacket.
 */
public record MyDuplicatesResponsePacket(List<String> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MyDuplicatesResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "my_duplicates_response"));

    public static final StreamCodec<ByteBuf, MyDuplicatesResponsePacket> CODEC =
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8)
            .map(
                (ArrayList<String> list) -> new MyDuplicatesResponsePacket(list),
                (MyDuplicatesResponsePacket packet) -> new ArrayList<>(packet.entries()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(MyDuplicatesResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientWhoNeedsHelper.setDuplicates(packet.entries()));
    }
}
