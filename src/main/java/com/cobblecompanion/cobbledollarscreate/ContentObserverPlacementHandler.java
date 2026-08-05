package com.cobblecompanion.cobbledollarscreate;

import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Nutzer-Vorgabe (verknüpfte Zähler/Abzieher-Gruppen, "differenzierte" Item-Abstimmung): ein neu
 * platzierter Schlauer Beobachter übernimmt die Gruppen-ID, die {@link ContentObserverInteractionHandler}
 * für GENAU DIESEN Platzierungs-Rechtsklick als "gerade gehaltenes Item ist abgestimmt auf ..."
 * vorgemerkt hat (siehe ContentObserverGroupTuningManager - einmaliger Verbrauch, kein dauerhaftes
 * "Werkzeug scharf schalten" mehr, siehe dessen Klassenkommentar). War kein Wert vorgemerkt (z.B.
 * weil ein anderes/frisches, nicht abgestimmtes Item platziert wurde), passiert nichts.
 */
public class ContentObserverPlacementHandler {

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getPlacedBlock().getBlock() instanceof SmartObserverBlock)) return;

        String pendingGroupId = ContentObserverGroupTuningManager.consumePending(player.getUUID());
        if (pendingGroupId == null) return;

        var dimension = player.level().dimension();
        var pos = event.getPos();
        ContentObserverConfigManager.BlockConfig cfg = ContentObserverConfigManager.get(dimension, pos);
        if (cfg == null) cfg = new ContentObserverConfigManager.BlockConfig();
        cfg.groupId = pendingGroupId;
        ContentObserverConfigManager.set(dimension, pos, cfg);

        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.contentobserver_group_joined", "This Content Observer joined the tuned group."));
    }
}
