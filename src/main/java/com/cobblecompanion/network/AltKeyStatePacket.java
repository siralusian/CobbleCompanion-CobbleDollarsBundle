package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.ClientKeyStateManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: ob der Spieler gerade Alt hält - Gegenstück zu CtrlKeyStatePacket, gleiches
 * Prinzip (wird nicht automatisch repliziert, nur bei Änderung gesendet). Nutzer-Vorgabe: Alt+
 * Rechtsklick als eigener Kurzbefehl für den CustomNPC-CobbleMerchant-Modus-Umschalter (siehe
 * CustomNpcCobbleMerchantInteractionHandler) - bewusst NICHT mehr Strg+Umschalt+Rechtsklick, da
 * sich das mit dem reinen Strg+Rechtsklick-Ticker/Kisten-Verknüpfungs-Kurzbefehl überschnitten hat
 * (ein Alt-freier Ctrl+Rechtsklick auf einen bereits Merchant-Modus-NPC wurde fälschlich von BEIDEN
 * Handlern verarbeitet, da der Verknüpfungs-Handler nicht auf Shift prüft).
 */
public record AltKeyStatePacket(boolean held) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AltKeyStatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "alt_key_state"));

    public static final StreamCodec<ByteBuf, AltKeyStatePacket> CODEC =
        ByteBufCodecs.BOOL.map(AltKeyStatePacket::new, AltKeyStatePacket::held);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AltKeyStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ClientKeyStateManager.setAltHeld(player.getUUID(), packet.held());
        });
    }
}
