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
 * Client -> Server: ob der Spieler gerade Strg hält. Anders als Shift/Sneak wird das nicht
 * automatisch als Teil des Spielerzustands repliziert - gesendet nur bei einer Änderung (siehe
 * ClientEventHandler.onClientTick), nicht jeden Tick. Wird für die Ctrl+Rechtsklick-Kurzbefehle
 * im Merchant-/Lagerticker-Bereich gebraucht (siehe ClientKeyStateManager-Klassenkommentar).
 */
public record CtrlKeyStatePacket(boolean held) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CtrlKeyStatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "ctrl_key_state"));

    public static final StreamCodec<ByteBuf, CtrlKeyStatePacket> CODEC =
        ByteBufCodecs.BOOL.map(CtrlKeyStatePacket::new, CtrlKeyStatePacket::held);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CtrlKeyStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ClientKeyStateManager.setCtrlHeld(player.getUUID(), packet.held());
        });
    }
}
