package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.TypeHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client -> Server: Anfrage nach dem Type-Effektivitäts-Report für eine Query
 * (Typname oder Pokemon-Name), gesendet vom Type-Tab.
 */
public record TypeRequestPacket(String query) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TypeRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "type_request"));

    public static final StreamCodec<ByteBuf, TypeRequestPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(TypeRequestPacket::new, TypeRequestPacket::query);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TypeRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                List<String> entries = TypeHelper.buildTypeResultsForGui(player, packet.query());
                if (entries == null) {
                    // Marker statt Fließtext - der Client übersetzt "H|" über einen Sprachschlüssel.
                    entries = List.of("H|" + packet.query());
                }
                PacketDistributor.sendToPlayer(player, new TypeResponsePacket(entries));
            }
        });
    }
}
