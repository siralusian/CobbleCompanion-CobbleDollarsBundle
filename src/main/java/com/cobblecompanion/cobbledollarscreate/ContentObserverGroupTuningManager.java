package com.cobblecompanion.cobbledollarscreate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nutzer-Vorgabe (verknüpfte Zähler/Abzieher-Gruppen, "differenzierte" Item-Abstimmung): merkt sich
 * für JEDEN Rechtsklick eines Spielers mit einem Schlauer-Beobachter-Item KURZZEITIG (bis zur
 * nächsten Blockplatzierung ODER dem nächsten Rechtsklick) dessen aktuelle Gruppen-Abstimmung
 * (aus der Datenkomponente des GEHALTENEN Items gelesen, siehe ContentObserverInteractionHandler) -
 * NICHT mehr dauerhaft "scharf" wie in der ersten Version. Grund (Nutzer-Fund, Live-Test): ein
 * frisches/anderes Item aus dem Inventar darf NICHT automatisch derselben Gruppe beitreten, nur
 * weil vorher mal ein anderes Item abgestimmt wurde - die Gruppen-Zugehörigkeit lebt jetzt auf dem
 * Item selbst (Datenkomponente), dieser Manager überbrückt nur die kurze Zeitspanne zwischen
 * "Rechtsklick mit Item X in der Hand" (ContentObserverInteractionHandler.onRightClickBlock, HEAD)
 * und "Block wird als Ergebnis dieses Klicks platziert" (ContentObserverPlacementHandler,
 * BlockEvent.EntityPlaceEvent) - danach wird der Wert sofort wieder konsumiert/gelöscht.
 */
public final class ContentObserverGroupTuningManager {

    private static final Map<UUID, String> pendingGroupId = new HashMap<>();

    private ContentObserverGroupTuningManager() {}

    /** null = das aktuell gehaltene Item ist NICHT abgestimmt (löscht eine evtl. vorherige Zuordnung wieder). */
    public static void setPending(UUID playerUuid, String groupId) {
        if (groupId == null) pendingGroupId.remove(playerUuid);
        else pendingGroupId.put(playerUuid, groupId);
    }

    /** Liest und löscht in einem Schritt - einmaliger Verbrauch pro Platzierung. */
    public static String consumePending(UUID playerUuid) {
        return pendingGroupId.remove(playerUuid);
    }
}
