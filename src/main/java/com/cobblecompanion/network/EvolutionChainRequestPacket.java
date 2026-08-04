package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.ServerDataHelper;
import com.cobblecompanion.data.TodoHelper;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.pokemon.Species;
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
 * Client -> Server: PC-Sortierhilfe (Living-Dex-Modus) braucht die Entwicklungskette einer Art,
 * um bei Duplikaten die nächste noch freie Zielstufe vorzuschlagen (siehe PCSortHelper).
 *
 * WICHTIG (per Live-Test gefunden - der eigentliche Grund, warum die Sortierhilfe nur im
 * Singleplayer funktionierte): Species.getEvolutions() liefert auf einem ECHTEN Server client-
 * seitig eine LEERE Menge, selbst für Arten mit echten Entwicklungen (bestätigt per Chat-Debug-
 * Log: "Evolutions-Anzahl=0" für Schiggy). Cobblemons Client-Sync der Spezies-Registry enthält
 * die Entwicklungsdaten offenbar nicht (Entwicklung läuft rein serverseitig ab, der Client
 * braucht sie normalerweise nicht) - im Singleplayer fiel das nie auf, weil dort Client und
 * Server im selben Prozess laufen und derselbe (vollständige) Species-Objektgraph genutzt wird.
 * Deshalb jetzt ein echter Server-Roundtrip: der Server hat die volle Registry und kann die
 * Kette zuverlässig berechnen, der Client cached das Ergebnis (siehe PCSortHelper.evolutionChainCache).
 */
public record EvolutionChainRequestPacket(String speciesName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EvolutionChainRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "evolution_chain_request"));

    public static final StreamCodec<ByteBuf, EvolutionChainRequestPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, EvolutionChainRequestPacket::speciesName,
        EvolutionChainRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EvolutionChainRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // Breitensuche statt linearer Kette: läuft über ALLE Verzweigungen (evo.getEvolutions()
            // kann mehrere Einträge haben, siehe Evoli mit 8 möglichen Entwicklungen), nicht nur
            // die erste. Bug-Fund (Live-Test): die alte Version nahm per "break" IMMER nur die
            // erste Entwicklung, weshalb bei Evoli-Duplikaten dieselbe einzelne Zielart (z.B.
            // Blitza) vorgeschlagen und danach sofort "nicht mehr gebraucht" gemeldet wurde, statt
            // die übrigen 7 Entwicklungsmöglichkeiten als weitere Zielslots anzubieten.
            List<String> chain = new ArrayList<>();
            java.util.Set<String> visited = new java.util.HashSet<>();
            java.util.Deque<Species> queue = new java.util.ArrayDeque<>();
            Species start = ServerDataHelper.findSpeciesByName(packet.speciesName());
            if (start != null) {
                visited.add(start.getName().toLowerCase());
                queue.add(start);
            }
            int guard = 0;
            while (!queue.isEmpty() && guard < 50) {
                Species current = queue.poll();
                for (Evolution evo : current.getEvolutions()) {
                    if (++guard >= 50) break;
                    ResourceLocation toId = TodoHelper.resolveSpeciesId(evo.getResult().getSpecies());
                    if (toId == null) continue;
                    Species next = PokemonSpecies.INSTANCE.getByIdentifier(toId);
                    if (next == null) {
                        // Pfad-Fallback wie überall sonst - Id-Mismatches sind in dieser Codebase
                        // ein wiederkehrendes Problem (siehe PCSortHelper.resolveSpeciesRobust()).
                        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
                            if (s.getResourceIdentifier().getPath().equalsIgnoreCase(toId.getPath())) {
                                next = s;
                                break;
                            }
                        }
                    }
                    if (next == null) continue;
                    String key = next.getName().toLowerCase();
                    if (!visited.add(key)) continue; // schon gesehen -> Zyklus-Schutz
                    // Geschlechts-Sperre dieses Zweigs (z.B. Kirlia->Gardevoir nur weiblich,
                    // ->Gallade nur männlich) mit ins Wire-Format packen - ":" als Trenner, GENDER-
                    // Name oder leer falls ungebunden (siehe EvolutionGenderHelper).
                    com.cobblemon.mod.common.pokemon.Gender requiredGender =
                        com.cobblecompanion.data.EvolutionGenderHelper.requiredGender(evo);
                    chain.add(next.getName() + ":" + (requiredGender != null ? requiredGender.name() : ""));
                    queue.add(next);
                }
            }

            PacketDistributor.sendToPlayer(player, new EvolutionChainResponsePacket(packet.speciesName(), chain));
        });
    }
}
