package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.LivingDexPlusRegistry;
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
 * Client -> Server: fordert den kompletten Living-Dex+-Katalog an (siehe LivingDexPlusRegistry).
 * Feldlos, wird EINMAL pro Session gesendet (beim ersten PC-Öffnen bzw. Settings-Tab-Öffnen im
 * Living-Dex+-Modus) - der Client cached die Antwort danach lokal (siehe LivingDexPlusHelper),
 * da es sich um statische Spieldaten handelt, die sich innerhalb einer Sitzung nie ändern.
 */
public record LivingDexPlusEnumerationRequestPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LivingDexPlusEnumerationRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "living_dex_plus_enum_request"));

    public static final StreamCodec<ByteBuf, LivingDexPlusEnumerationRequestPacket> CODEC =
        StreamCodec.unit(new LivingDexPlusEnumerationRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LivingDexPlusEnumerationRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            List<String> wire = new ArrayList<>();
            for (LivingDexPlusRegistry.Entry entry : LivingDexPlusRegistry.getAll()) {
                wire.add(entry.categoryId() + "|" + entry.speciesName() + "|" + entry.formName() + "|" + entry.dexNumber());
            }

            PacketDistributor.sendToPlayer(player, new LivingDexPlusEnumerationResponsePacket(wire));
        });
    }
}
