package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: AdminOp-Editor-Overlay im Professor-Tab wurde mit "Speichern" bestätigt.
 * Ändert Level/Shiny-Status/Fangball/Wesen/Geschlecht/Spitzname eines Pokemons im Team/PC des
 * ausgewählten Spielers. Braucht AdminOp (strenger als der reine Op-Leserechte-Check). Funktioniert
 * bewusst auch bei OFFLINE Zielspielern - nutzt dieselbe UUID-basierte Storage-API wie
 * PlayerDataHelper.getAllPokemonByUuid (Cobblemons eigene Offline-Zugriffsmethode), damit die
 * Mutation ganz normal serverseitig persistiert wird, egal ob der Spieler gerade online ist.
 */
public record AdminEditPokemonPacket(UUID targetUuid, UUID pokemonUuid, int newLevel, boolean newShiny,
                                      String newPokeballId, String newNature, String newGender, String newNickname,
                                      String newAbility)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminEditPokemonPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_edit_pokemon"));

    // StreamCodec.composite unterstützt maximal 5 Feld-Paare - Pokéball/Wesen/Geschlecht/
    // Spitzname/Fähigkeit werden deshalb zu EINEM pipe-getrennten String zusammengefasst
    // (Spitzname darf dafür bewusst kein "|" enthalten, siehe charTyped()-Filter in CompanionScreen).
    public static final StreamCodec<ByteBuf, AdminEditPokemonPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, AdminEditPokemonPacket::targetUuid,
        UUIDUtil.STREAM_CODEC, AdminEditPokemonPacket::pokemonUuid,
        ByteBufCodecs.VAR_INT, AdminEditPokemonPacket::newLevel,
        ByteBufCodecs.BOOL, AdminEditPokemonPacket::newShiny,
        ByteBufCodecs.STRING_UTF8,
        (AdminEditPokemonPacket p) -> p.newPokeballId() + "|" + p.newNature() + "|" + p.newGender() + "|" + p.newNickname() + "|" + p.newAbility(),
        (targetUuid, pokemonUuid, newLevel, newShiny, combined) -> {
            String[] parts = combined.split("\\|", -1);
            return new AdminEditPokemonPacket(targetUuid, pokemonUuid, newLevel, newShiny,
                parts.length > 0 ? parts[0] : "", parts.length > 1 ? parts[1] : "",
                parts.length > 2 ? parts[2] : "", parts.length > 3 ? parts[3] : "",
                parts.length > 4 ? parts[4] : "");
        });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminEditPokemonPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin)) return;
            if (!AdminPermissionManager.isAdminOp(admin.getUUID())) return;
            MinecraftServer server = admin.getServer();
            if (server == null) return;

            Pokemon pokemon = PlayerDataHelper.findPokemonByUuid(packet.targetUuid(), packet.pokemonUuid(), server.registryAccess());
            if (pokemon == null) return;

            pokemon.setLevel(Math.max(1, Math.min(100, packet.newLevel())));
            pokemon.setShiny(packet.newShiny());
            try {
                ResourceLocation ballId = ResourceLocation.fromNamespaceAndPath("cobblemon", packet.newPokeballId());
                PokeBall ball = PokeBalls.INSTANCE.getPokeBall(ballId);
                if (ball != null) pokemon.setCaughtBall(ball);
            } catch (Exception ignored) {}
            try {
                Nature nature = Natures.getNature(packet.newNature());
                if (nature != null) pokemon.setNature(nature);
            } catch (Exception ignored) {}
            try {
                pokemon.setGender(Gender.valueOf(packet.newGender()));
            } catch (Exception ignored) {}
            try {
                if (!packet.newNickname().isBlank()) {
                    pokemon.setNickname(Component.literal(packet.newNickname()));
                } else {
                    pokemon.setNickname(null);
                }
            } catch (Exception ignored) {}
            // Fähigkeit: nutzt exakt denselben Mechanismus wie Cobblemons eigene /pokemonedit(other)-
            // Befehle (PokemonProperties.parse("ability=<id>").apply(pokemon)) statt einer eigenen
            // Ability-Konstruktion - so bleibt jede Sonderbehandlung (Priorität, geerbte Aspekte
            // etc.) exakt wie im Original. Bewusst NICHT der Befehl selbst (per performPrefixedCommand
            // dispatcht): Cobblemons PartySlotArgumentType kann nur Team-Slots adressieren, unser
            // Editor bearbeitet aber auch PC-Pokemon - deshalb direkter API-Aufruf auf dem bereits
            // per UUID aufgelösten Pokemon statt über den (Team-Slot-limitierten) Chat-Befehl.
            try {
                if (!packet.newAbility().isBlank()) {
                    com.cobblemon.mod.common.api.pokemon.PokemonProperties props =
                        com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("ability=" + packet.newAbility());
                    props.apply(pokemon);
                }
            } catch (Exception ignored) {}

            ProfessorPCRequestPacket.buildAndSend(admin, server, packet.targetUuid());
        });
    }
}
