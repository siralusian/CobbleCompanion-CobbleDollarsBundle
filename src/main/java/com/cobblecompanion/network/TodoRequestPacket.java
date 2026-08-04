package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.TodoHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Anfrage nach der aktuellen ToDo-Zusammenfassung, gesendet
 * beim Wechsel auf den ToDo-Tab.
 */
public record TodoRequestPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TodoRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "todo_request"));

    public static final StreamCodec<ByteBuf, TodoRequestPacket> CODEC =
        StreamCodec.unit(new TodoRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TodoRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new TodoResponsePacket(TodoHelper.getTodoEntries(player)));
            }
        });
    }
}
