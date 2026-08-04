package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientHomeHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: Antwort auf HomeSummaryRequestPacket (nur gesendet, wenn "Slot Prüfung"
 * aktiv ist) - Anzahl der PC-Pokemon außerhalb ihrer Dex-Box, siehe PCSlotCheckHelper. Eigenes
 * Packet statt eines 6. Feldes auf HomeSummaryResponsePacket, da StreamCodec.composite dort schon
 * bei 5 Feld-Paaren liegt (der in dieser Codebase etablierte Deckel).
 */
public record HomeSlotCheckResponsePacket(int misplacedCount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HomeSlotCheckResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "home_slot_check_response"));

    public static final StreamCodec<ByteBuf, HomeSlotCheckResponsePacket> CODEC =
        ByteBufCodecs.VAR_INT.map(HomeSlotCheckResponsePacket::new, HomeSlotCheckResponsePacket::misplacedCount);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HomeSlotCheckResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientHomeHelper.setSlotCheckResult(packet.misplacedCount()));
    }
}
