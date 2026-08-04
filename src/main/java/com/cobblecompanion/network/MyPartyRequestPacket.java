package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> Server: fordert die eigene Party an (für das Party-Auswahl-Overlay beim
 * Pokemon-Verschenken im Friends-Tab). Antwort: MyPartyResponsePacket.
 */
public record MyPartyRequestPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MyPartyRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "my_party_request"));

    public static final StreamCodec<ByteBuf, MyPartyRequestPacket> CODEC =
        StreamCodec.unit(new MyPartyRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MyPartyRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            List<String> entries = new ArrayList<>();
            for (Pokemon p : PlayerDataHelper.getParty(player)) {
                String nickname = p.getNickname() != null ? p.getNickname().getString().replace("|", "") : "";
                entries.add(p.getUuid() + "|" + p.getSpecies().getResourceIdentifier() + "|" + p.getLevel()
                    + "|" + String.join(",", p.getAspects()) + "|" + nickname);
            }
            PacketDistributor.sendToPlayer(player, new MyPartyResponsePacket(entries));
        });
    }
}
