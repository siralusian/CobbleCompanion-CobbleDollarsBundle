package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientTodoHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: aktuelle ToDo-Einträge als Antwort auf TodoRequestPacket. Jeder Eintrag
 * ist eine Pipe-getrennte Zeile
 * "pokemonUuid|fromSpeciesId|fromLevel|fromAspects|toSpeciesId|canEvolveNow|itemId|needsPokedex|needsLivingDex|isParty|pcBox|pcBoxName|nickname|caughtBallId"
 * (siehe TodoHelper.getTodoEntries) - der Client baut daraus das Slot-Layout, filtert nach
 * Setting und gruppiert nach Party/PC + PC-Box (inkl. Box-Unterüberschrift).
 */
public record TodoResponsePacket(List<String> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TodoResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "todo_response"));

    public static final StreamCodec<ByteBuf, TodoResponsePacket> CODEC =
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8)
            .map(
                (ArrayList<String> list) -> new TodoResponsePacket(list),
                (TodoResponsePacket packet) -> new ArrayList<>(packet.entries()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): der RuntimeDistCleaner von FML entfernt sonst diese
    // Methode beim Klassenladen auf dem Dedicated Server (siehe LivingDexPacket.handle).
    public static void handle(TodoResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientTodoHelper.setEntries(packet.entries()));
    }
}
