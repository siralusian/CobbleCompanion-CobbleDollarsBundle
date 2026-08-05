package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.integrations.ModAvailability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Gemeinsame Auszahlungslogik für alle vier "Schlauer Beobachter"-Erkennungspfade (Förderband,
 * Schacht, Trichter-Übergabe, generischer Rest-Fall) - siehe ContentObserverBeltRewardMixin/
 * ContentObserverChuteRewardMixin/ContentObserverFunnelTransferMixin/ContentObserverActivateMixin.
 *
 * Erweiterung (Nutzer-Vorgabe, Lagernetzwerk-Preise): {@link ContentObserverConfigManager.Rule#amountPerItem}
 * wird über die "Nutze Ankaufspreis"/"Nutze Verkaufspreis"-Buttons im Editor bewusst NUR EINMALIG
 * beim Speichern aus der Netzwerk-Preisliste in den manuellen Betrag KOPIERT (siehe
 * ContentObserverConfigScreen), statt live bei jeder Auszahlung neu aufgelöst zu werden.
 *
 * Erweiterung (Nutzer-Vorgabe, verknüpfte Zähler/Abzieher-Gruppen, 3. Live-Test): der frühere feste
 * "Rolle"-Schalter pro Block ist entfallen - ein gruppierter Block prüft jetzt BEIDE Regelsätze
 * unabhängig ({@link ContentObserverConfigManager#effectiveCounterRules}/{@link
 * ContentObserverConfigManager#effectiveSubtractorRules}) und kann für ein und dasselbe erkannte
 * Item-Ereignis gleichzeitig zählen UND abziehen (theoretisch möglich, praktisch meist nur eines
 * von beidem, je nachdem welche Checkboxen im Editor gesetzt sind). {@code matchedStack} ist NUR
 * für den generischen Activate-Fallback null (siehe ContentObserverActivateMixin) - der läuft
 * ausschließlich für ungruppierte Blöcke mit genau 1 Regel, dort wird ohne echten ItemStack direkt
 * diese eine Regel verwendet statt sie gegen einen Stack zu matchen.
 */
public final class ContentObserverRewardBridge {

    private ContentObserverRewardBridge() {}

    /** Zentrale Anlaufstelle für alle vier Erkennungspfade. */
    public static void handleDetectedItems(ServerLevel level, BlockPos pos,
            ContentObserverConfigManager.BlockConfig cfg, ItemStack matchedStack, int itemCount) {
        if (!ModAvailability.isCobbleDollarsAvailable()) return;
        if (cfg == null || itemCount <= 0) return;

        if (cfg.groupId == null) {
            handleUngrouped(level, cfg, matchedStack, itemCount);
            return;
        }
        if (matchedStack == null) return; // siehe Klassenkommentar - Activate-Fallback ist immer ungruppiert

        String itemKey = itemKeyFor(matchedStack);

        ContentObserverConfigManager.Rule counterRule = ContentObserverConfigManager.findMatchingRule(
            ContentObserverConfigManager.effectiveCounterRules(cfg), matchedStack);
        if (counterRule != null && counterRule.targetPlayerUuid != null && counterRule.amountPerItem != 0) {
            ContentObserverPromiseManager.count(level.getServer(), cfg.groupId, itemKey, itemCount,
                counterRule.targetPlayerUuid, counterRule.amountPerItem, cfg.promiseExpiryStage);
        }

        ContentObserverConfigManager.Rule subtractorRule = ContentObserverConfigManager.findMatchingRule(
            ContentObserverConfigManager.effectiveSubtractorRules(cfg), matchedStack);
        if (subtractorRule != null) {
            // Nutzer-Vorgabe (Korrektur nach 3. Live-Test): der EIGENE Preis des Abziehers bestimmt
            // die sofort verrechnete Differenz zum Zähler-Preis - siehe ContentObserverPromiseManager-Klassenkommentar.
            ContentObserverPromiseManager.subtract(level.getServer(), cfg.groupId, itemKey, itemCount, subtractorRule.amountPerItem);
        }

        if (counterRule == null && subtractorRule == null) {
            CobbleCompanionDollarsCreate.LOGGER.info(
                "[CC] Beobachter {} sieht {}x {}, aber keine aktivierte Katalog-Regel (Zähler oder Abzieher) passt (Gruppe {}).",
                pos, itemCount, itemKey, cfg.groupId);
        }
    }

    /** Ungruppierter Block: sofortige Auszahlung, kein Zähler/Abzieher-Konzept. */
    private static void handleUngrouped(ServerLevel level, ContentObserverConfigManager.BlockConfig cfg,
            ItemStack matchedStack, int itemCount) {
        List<ContentObserverConfigManager.Rule> rules = cfg.rules;
        ContentObserverConfigManager.Rule rule = matchedStack != null
            ? ContentObserverConfigManager.findMatchingRule(rules, matchedStack)
            : (rules != null && rules.size() == 1 ? rules.get(0) : null); // Activate-Fallback, siehe Klassenkommentar
        if (rule == null || rule.targetPlayerUuid == null || rule.amountPerItem == 0) return;
        payout(level.getServer(), rule.targetPlayerUuid, rule.amountPerItem * (long) itemCount);
    }

    private static String itemKeyFor(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Zahlt sofort aus (amount darf negativ sein = Abzug) - Zielspieler offline landet in PendingCobbleDollarsManager. */
    public static void payout(MinecraftServer server, String targetPlayerUuid, long amount) {
        if (server == null || targetPlayerUuid == null || amount == 0) return;
        UUID uuid;
        try {
            uuid = UUID.fromString(targetPlayerUuid);
        } catch (IllegalArgumentException e) {
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(uuid);
        if (target == null) {
            com.cobblecompanion.data.PendingCobbleDollarsManager.addPending(uuid, amount);
            return;
        }
        com.cobblecompanion.integrations.create.ContentObserverRewardManager.reward(target, amount);
    }
}
