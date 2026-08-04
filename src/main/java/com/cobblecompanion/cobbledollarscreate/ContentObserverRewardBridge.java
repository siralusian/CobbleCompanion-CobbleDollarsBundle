package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.integrations.ModAvailability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Gemeinsame Auszahlungslogik für alle drei "Schlauer Beobachter"-Erkennungspfade (Förderband,
 * Trichter-Übergabe, generischer Rest-Fall) - löst den konfigurierten Ziel-Spieler auf und reicht
 * an ContentObserverRewardManager (bleibt in Basis, siehe deren Klassenkommentar) weiter (Sofort-
 * Gutschrift, gesammelte Chat-Meldung). Siehe ContentObserverBeltRewardMixin/
 * ContentObserverFunnelTransferMixin/ContentObserverActivateMixin.
 *
 * Bugfix (Nutzer-Fund): ist der Zielspieler offline, kann CobbleDollarsBridge sein Guthaben nicht
 * verändern (grant/charge brauchen eine echte ServerPlayer-Instanz) - die Belohnung verfiel bisher
 * ersatzlos. Läuft jetzt stattdessen in PendingCobbleDollarsManager (bleibt in Basis) auf, der
 * Spieler holt sie sich beim nächsten Login selbst über das Home-Tab ab.
 */
public final class ContentObserverRewardBridge {

    private ContentObserverRewardBridge() {}

    public static void rewardForItems(ServerLevel level, ContentObserverConfigManager.Entry cfg, int itemCount) {
        if (!ModAvailability.isCobbleDollarsAvailable()) return;
        // Nutzer-Vorgabe: amountPerItem darf jetzt auch negativ sein (Abzug statt Gutschrift, siehe
        // ContentObserverRewardManager) - nur amountPerItem==0 (nichts konfiguriert) blockt weiterhin.
        if (cfg == null || cfg.targetPlayerUuid == null || cfg.amountPerItem == 0 || itemCount <= 0) return;

        UUID uuid;
        try {
            uuid = UUID.fromString(cfg.targetPlayerUuid);
        } catch (IllegalArgumentException e) {
            return;
        }
        long amount = cfg.amountPerItem * (long) itemCount;

        ServerPlayer target = level.getServer().getPlayerList().getPlayer(uuid);
        if (target == null) {
            com.cobblecompanion.data.PendingCobbleDollarsManager.addPending(uuid, amount);
            return;
        }

        com.cobblecompanion.integrations.create.ContentObserverRewardManager.reward(target, amount);
    }
}
