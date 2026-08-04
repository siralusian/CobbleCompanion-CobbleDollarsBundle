package com.cobblecompanion.cobbledollars.network;

import com.cobblecompanion.cobbledollars.CobbleCompanionDollars;
import com.cobblecompanion.data.CreativeTimeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Spieler mit noch übriger gekaufter Creative-Zeit wechselt selbst zwischen
 * Survival, Creative und Spectator (Wallet-Tab). Wirkungslos, wenn keine aktive Session besteht
 * (siehe CreativeTimeManager.switchGameMode) - Restzeit läuft dabei unabhängig vom aktuellen
 * Modus weiter. mode: 0=Survival, 1=Creative, 2=Spectator.
 */
public record CreativeTimeGameModeSwitchPacket(int mode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreativeTimeGameModeSwitchPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollars.MOD_ID, "creative_time_gamemode_switch"));

    public static final StreamCodec<ByteBuf, CreativeTimeGameModeSwitchPacket> CODEC =
        ByteBufCodecs.VAR_INT.map(CreativeTimeGameModeSwitchPacket::new, CreativeTimeGameModeSwitchPacket::mode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreativeTimeGameModeSwitchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            GameType target = switch (packet.mode()) {
                case 1 -> GameType.CREATIVE;
                case 2 -> GameType.SPECTATOR;
                default -> GameType.SURVIVAL;
            };
            CreativeTimeManager.switchGameMode(player, target);
        });
    }
}
