package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.ServerDataHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> Server: Anfrage nach den eigenen abgebbaren Duplikaten für den Who-Needs-Tab.
 * livingDexMode steuert die Reserve-Berechnung (siehe ServerDataHelper.getMyDuplicateSpecies).
 */
public record MyDuplicatesRequestPacket(boolean livingDexMode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MyDuplicatesRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "my_duplicates_request"));

    public static final StreamCodec<ByteBuf, MyDuplicatesRequestPacket> CODEC =
        ByteBufCodecs.BOOL.map(MyDuplicatesRequestPacket::new, MyDuplicatesRequestPacket::livingDexMode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MyDuplicatesRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                List<String> entries = new ArrayList<>();
                for (ServerDataHelper.DuplicateEntry e : ServerDataHelper.getMyDuplicateSpecies(player, packet.livingDexMode())) {
                    entries.add(e.speciesId + "|" + e.level + "|" + String.join(",", e.aspects));
                }
                PacketDistributor.sendToPlayer(player, new MyDuplicatesResponsePacket(entries));
            }
        });
    }
}
