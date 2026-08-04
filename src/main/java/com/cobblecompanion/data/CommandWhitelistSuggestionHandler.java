package com.cobblecompanion.data;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Nutzer-Fund (Live-Test): freigeschaltete Befehle (siehe CommandWhitelistManager/
 * CommandWhitelistHandler) werden für restricted-Creative-Spieler zwar akzeptiert und ausgeführt,
 * tauchen aber NICHT in der Tab-Vervollständigung auf. Root Cause: Brigadier generiert
 * Vervollständigungsvorschläge, indem es unabhängig von jeglicher Ausführung die requires()-
 * Prädikate der Befehlsbaum-Knoten gegen die ECHTE (nicht angehobene) CommandSourceStack des
 * Spielers prüft - das ist ein komplett anderer Codepfad als CommandEvent (auf dem
 * CommandWhitelistHandler aufbaut), das erst NACH einem bereits erfolgreichen Parse feuert.
 *
 * Fix: das requires()-Prädikat jedes Wurzel-Knotens, dessen Name in einem konfigurierten
 * Whitelist-Muster als erstes Wort vorkommt (siehe CommandWhitelistManager.getRootLiterals), per
 * Reflection um "ODER: Spieler ist restricted-Creative" erweitern - Brigadiers CommandNode
 * speichert das Prädikat in einem privaten Feld ohne öffentlichen Setter, deshalb Reflection statt
 * normalem API-Aufruf.
 *
 * WICHTIG zum Timing: RegisterCommandsEvent (wo der Befehlsbaum entsteht) feuert bereits während
 * der MinecraftServer-Konstruktion, also VOR ServerStartingEvent - zu diesem Zeitpunkt hat
 * CommandWhitelistManager.init() noch NICHT geladen, "getRootLiterals()" wäre also immer leer.
 * Deshalb wird refresh() stattdessen bei ServerStartedEvent (garantiert nach allen Manager-Inits)
 * UND erneut bei jeder Änderung der Whitelist per Befehl aufgerufen (siehe
 * CobbleCompanionCommands.addCommandWhitelistPattern/removeCommandWhitelistPattern) - der
 * CommandDispatcher selbst bleibt für die gesamte Serverlaufzeit dieselbe Instanz, ein erneutes
 * "Registrieren" ist nicht nötig, nur das erneute Anpassen der bereits bestehenden Knoten.
 *
 * Bewusster Kompromiss: diese Lockerung wirkt nur auf der WURZEL-Ebene (z.B. "tag"), nicht auf
 * einzelne Unterbefehle - ein Muster wie "tag add @p Test" (ohne Wildcard, nur ein exakter Befehl
 * erlaubt) macht dadurch die GESAMTE "tag ..."-Verzweigung vorschlagbar, auch wenn beim
 * tatsächlichen Ausführen weiterhin nur genau der eine erlaubte Befehl durchkommt (siehe
 * CommandWhitelistManager.isAllowed - dort bleibt die exakte Prüfung unverändert bestehen). Für
 * eine reine Komfortfunktion (Tab-Vervollständigung) ist diese Ungenauigkeit hinnehmbar.
 */
public final class CommandWhitelistSuggestionHandler {

    private static Field requirementField;

    // Merkt sich das ECHTE ursprüngliche Prädikat pro Knoten (vor unserer eigenen Erweiterung) -
    // verhindert, dass wiederholte refresh()-Aufrufe (z.B. nach jedem "add"/"remove") das Prädikat
    // immer tiefer ineinander verschachteln, statt es sauber neu aus dem Original aufzubauen.
    private static final Map<CommandNode<?>, Predicate<?>> originalRequirements = new IdentityHashMap<>();

    private CommandWhitelistSuggestionHandler() {}

    public static void refresh(MinecraftServer server) {
        Set<String> rootLiterals = CommandWhitelistManager.getRootLiterals();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

        for (CommandNode<CommandSourceStack> child : dispatcher.getRoot().getChildren()) {
            boolean shouldRelax = rootLiterals.contains(child.getName());
            Predicate<CommandSourceStack> original = original(child);
            setRequirement(child, shouldRelax ? relaxed(original) : original);
        }
    }

    @SuppressWarnings("unchecked")
    private static Predicate<CommandSourceStack> original(CommandNode<CommandSourceStack> node) {
        return (Predicate<CommandSourceStack>) originalRequirements.computeIfAbsent(node, CommandNode::getRequirement);
    }

    private static Predicate<CommandSourceStack> relaxed(Predicate<CommandSourceStack> original) {
        return source -> {
            if (original.test(source)) return true;
            return source.getEntity() instanceof ServerPlayer player && CreativeRestrictionHandler.isRestricted(player);
        };
    }

    private static void setRequirement(CommandNode<CommandSourceStack> node, Predicate<CommandSourceStack> predicate) {
        try {
            if (requirementField == null) {
                Field f = CommandNode.class.getDeclaredField("requirement");
                f.setAccessible(true);
                requirementField = f;
            }
            requirementField.set(node, predicate);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
