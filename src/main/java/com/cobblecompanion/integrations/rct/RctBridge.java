package com.cobblecompanion.integrations.rct;

import com.gitlab.srcmc.rctmod.api.RCTMod;
import com.gitlab.srcmc.rctmod.api.data.pack.SeriesMetaData;
import com.gitlab.srcmc.rctmod.api.data.save.TrainerPlayerData;
import com.gitlab.srcmc.rctmod.api.service.SeriesManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Einzige Klasse, die direkt gegen die RCT-API (rctmod/rctapi) programmiert - siehe
 * com.cobblecompanion.integrations.package-info für die Isolationsregel. Aufrufer müssen VORHER
 * ModAvailability.isRctAvailable() geprüft haben.
 */
public final class RctBridge {

    private RctBridge() {}

    public record SeriesEntry(String id, String title, boolean completed) {}

    /** Alle registrierten Trainerpfade (Serien) mit Abschluss-Status für den Zielspieler. */
    public static List<SeriesEntry> listSeries(ServerPlayer target) {
        SeriesManager seriesManager = RCTMod.getInstance().getSeriesManager();
        TrainerPlayerData data = RCTMod.getInstance().getTrainerManager().getData(target);
        Set<String> completedIds = data.getCompletedSeries().keySet();

        List<SeriesEntry> entries = new ArrayList<>();
        for (String id : new TreeSet<>(seriesManager.getSeriesIds())) {
            if (id.equals(SeriesManager.EMPTY_SERIES_ID) || id.equals(SeriesManager.FREEROAM_SERIES_ID)) continue;
            SeriesManager.SeriesGraph graph = seriesManager.getGraph(id);
            if (graph == null) continue;
            SeriesMetaData meta = graph.getMetaData();
            String title = (meta != null && meta.title() != null)
                ? meta.title().getComponent().getString() : id;
            entries.add(new SeriesEntry(id, title, completedIds.contains(id)));
        }
        return entries;
    }

    /** Setzt EINEN Trainerpfad (Serie) inkl. seiner besiegten Trainer auf "nicht abgeschlossen" zurück. */
    public static void resetSeries(ServerPlayer target, String seriesId) {
        TrainerPlayerData data = RCTMod.getInstance().getTrainerManager().getData(target);
        data.removeSeriesCompletion(seriesId);
        removeDefeatedTrainersOfSeries(seriesId, data);
        if (seriesId.equals(data.getCurrentSeries())) {
            data.setCurrentSeries(SeriesManager.FREEROAM_SERIES_ID);
        }
        data.sync();
    }

    /** Setzt den KOMPLETTEN RCT-Fortschritt zurück (alle Serien + alle besiegten Trainer). */
    public static void resetAll(ServerPlayer target) {
        TrainerPlayerData data = RCTMod.getInstance().getTrainerManager().getData(target);
        data.removeProgressDefeats();
        for (String id : new ArrayList<>(data.getCompletedSeries().keySet())) {
            data.removeSeriesCompletion(id);
        }
        data.setCurrentSeries(SeriesManager.FREEROAM_SERIES_ID);
        data.sync();
    }

    private static void removeDefeatedTrainersOfSeries(String seriesId, TrainerPlayerData data) {
        SeriesManager.SeriesGraph graph = RCTMod.getInstance().getSeriesManager().getGraph(seriesId);
        if (graph == null) return;
        // Nur die Trainer DIESER Serie entfernen (nicht data.removeProgressDefeats(), das würde
        // alle Serien gleichzeitig betreffen).
        graph.stream().forEach(node -> data.removeProgressDefeat(node.id()));
    }
}
