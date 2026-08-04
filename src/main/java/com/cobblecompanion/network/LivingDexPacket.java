package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientLivingDexHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record LivingDexPacket(List<String> livingDexSpecies) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LivingDexPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "living_dex"));

    public static final StreamCodec<ByteBuf, LivingDexPacket> CODEC =
        ByteBufCodecs.collection(java.util.ArrayList::new, ByteBufCodecs.STRING_UTF8)
            .map(
                (java.util.ArrayList<String> list) -> new LivingDexPacket(list),
                (LivingDexPacket packet) -> new java.util.ArrayList<>(packet.livingDexSpecies()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): der RuntimeDistCleaner von FML entfernt sonst diese
    // Methode beim Klassenladen auf dem Dedicated Server, obwohl sie dort nur bei der
    // Paket-Registrierung referenziert (nie ausgeführt) wird -> NoSuchMethodError.
    public static void handle(LivingDexPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Set<String> species = new HashSet<>(packet.livingDexSpecies());
            ClientLivingDexHelper.setLivingDexSpecies(species);
            CobbleCompanion.LOGGER.info("[CC] Received Living Dex data: " + species.size() + " species");
        });
    }
}