package com.cobblecompanion.integrations.create;

import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsBridge;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nutzer-Vorgabe (Umbau): die früher hier eigenständige Chat-Bündelung (min. 5s Ruhe / max. 60s
 * Gesamtdauer) ist entfallen - die Meldung läuft jetzt gemeinsam mit der Online-Belohnung über
 * DEREN Timer (siehe OnlineRewardManager.tick, "Nachrichten-Intervall an den Online-Belohnungs-
 * Timer anpassen"). Diese Klasse sammelt deshalb nur noch die seit der letzten Auszahlung
 * aufgelaufenen Einnahmen/Ausgaben pro Spieler (rein flüchtig) - OnlineRewardManager holt sie sich
 * per pullPending() ab, sobald IHR Intervall abläuft, und baut daraus die gemeinsame Meldung
 * (siehe AutomaticIncomeReportPacket).
 *
 * Die eigentliche Cobbledollars-Bewegung passiert weiterhin SOFORT bei jedem einzelnen reward()-
 * Aufruf (kein Verlustrisiko bei Serverabsturz) - nur Chat-Meldung UND Transaktions-Log-Eintrag
 * werden jetzt gebündelt (Nutzer-Vorgabe: "im Log zusammenfassen" - vorher gab es einen Log-
 * Eintrag PRO Item-Erkennung, das lief bei Förderband-/Trichter-Ketten schnell voll).
 *
 * Nutzer-Vorgabe (aus dem vorherigen Umbau): amount darf negativ sein, um dem Spieler
 * Cobbledollars ABZUZIEHEN statt gutzuschreiben. Positiv -> grant() (immer erfolgreich), negativ
 * -> charge() des Betrags (schlägt bei zu wenig Guthaben fehl - dann einfach KEINE Abbuchung, das
 * physische Item wurde ja bereits verarbeitet und kann nicht rückgängig gemacht werden).
 */
public final class ContentObserverRewardManager {

    private static final Map<UUID, Long> pendingIncome = new HashMap<>();
    private static final Map<UUID, Long> pendingExpense = new HashMap<>();

    private ContentObserverRewardManager() {}

    /** Bucht den Betrag sofort gut/ab, sammelt den Betrag nur für die spätere gemeinsame Meldung. */
    public static void reward(ServerPlayer player, long amount) {
        if (amount == 0) return;
        boolean applied = amount > 0
            ? CobbleDollarsBridge.grant(player, BigInteger.valueOf(amount))
            : CobbleDollarsBridge.charge(player, BigInteger.valueOf(-amount));
        if (!applied) return;

        UUID uuid = player.getUUID();
        if (amount > 0) {
            pendingIncome.merge(uuid, amount, Long::sum);
        } else {
            pendingExpense.merge(uuid, -amount, Long::sum);
        }
    }

    /**
     * Holt die seit dem letzten Aufruf aufgelaufenen Beträge für diesen Spieler ab und setzt sie
     * zurück - Aufrufer: OnlineRewardManager.tick(), einmal pro dessen Intervall pro Spieler.
     *
     * @return {@code long[]{income, expense}}, beide &gt;= 0.
     */
    public static long[] pullPending(UUID uuid) {
        Long income = pendingIncome.remove(uuid);
        Long expense = pendingExpense.remove(uuid);
        return new long[] { income != null ? income : 0L, expense != null ? expense : 0L };
    }
}
