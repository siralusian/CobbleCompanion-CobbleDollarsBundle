package com.cobblecompanion.cobbledollarscreate.client;

import com.cobblecompanion.client.data.ClientNetworkUtil;
import com.cobblecompanion.cobbledollarscreate.ContentObserverDataComponents;
import com.cobblecompanion.cobbledollarscreate.ContentObserverGroupData;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientContentObserverHighlightHelper;
import com.cobblecompanion.cobbledollarscreate.network.ContentObserverGroupHighlightRequestPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Nutzer-Vorgabe: Grün-Rahmen um alle Blöcke der Zähler/Abzieher-Gruppe, auf die das gehaltene
 * Schlauer-Beobachter-Item abgestimmt ist - Nachbau von Creates eigenem Lagernetzwerk-Highlight
 * (siehe LogisticallyLinkedClientHandler.tick(), das dort Catnips Outliner-System nutzt, welches
 * für uns nicht auf dem Compile-Classpath verfügbar ist) mit reinen Vanilla-/NeoForge-Mitteln:
 * {@link RenderLevelStageEvent} + {@link LevelRenderer#renderLineBox} (dieselbe Vanilla-Hilfsmethode,
 * die auch der normale Block-Hit-Umriss benutzt). Helles Grün statt Creates Blau (Nutzer-Vorgabe),
 * pulsierend im 8-Tick-Rhythmus wie beim Vorbild.
 *
 * Bewusst KEIN @EventBusSubscriber (importiert direkt SmartObserverBlock aus Create) - manuell in
 * CobbleCompanionDollarsCreate.clientSetup() registriert, mit ModList-Gate, gleiches Muster wie
 * StockTickerPriceOverlay (siehe dessen Klassenkommentar für das WARUM).
 */
public final class ContentObserverGroupHighlightRenderer {

    private static final float[] COLOR_DIM = {0.25f, 0.75f, 0.25f};
    private static final float[] COLOR_BRIGHT = {0.55f, 1f, 0.55f};
    private static final int HIGHLIGHT_REFRESH_INTERVAL_TICKS = 20; // ~1 Sekunde
    /** Nutzer-Vorgabe: Rahmen ca. 3x so dick wie zuvor (2 Layer bis 0.006) - siehe onRenderLevelStage. */
    private static final double[] LINE_LAYER_INFLATE = {0.002, 0.008, 0.014, 0.02, 0.028, 0.038};

    private String lastRequestedGroupId = null;
    private int refreshCounter = 0;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        var player = Minecraft.getInstance().player;
        String heldGroupId = tunedGroupIdOf(player != null ? player.getMainHandItem() : ItemStack.EMPTY);

        if (heldGroupId == null) {
            lastRequestedGroupId = null;
            refreshCounter = 0;
            if (!ClientContentObserverHighlightHelper.getPositions().isEmpty()) {
                ClientContentObserverHighlightHelper.setPositions(List.of());
            }
            return;
        }

        refreshCounter++;
        boolean changed = !heldGroupId.equals(lastRequestedGroupId);
        if (changed || refreshCounter >= HIGHLIGHT_REFRESH_INTERVAL_TICKS) {
            refreshCounter = 0;
            lastRequestedGroupId = heldGroupId;
            ContentObserverGroupHighlightRequestPacket payload = new ContentObserverGroupHighlightRequestPacket(heldGroupId);
            if (ClientNetworkUtil.canSendToServerOrWarn(payload.type().id())) {
                PacketDistributor.sendToServer(payload);
            }
        }
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (tunedGroupIdOf(player.getMainHandItem()) == null) return;

        List<BlockPos> positions = ClientContentObserverHighlightHelper.getPositions();
        if (positions.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        var vertexConsumer = buffers.getBuffer(RenderType.lines());

        float[] color = (mc.level.getGameTime() % 16L) < 8L ? COLOR_BRIGHT : COLOR_DIM;

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        for (BlockPos pos : positions) {
            if (!mc.level.hasChunkAt(pos)) continue;
            // Nutzer-Vorgabe (Live-Test, "3x so dick wie jetzt"): renderLineBox zeichnet nur 1px
            // dünne Linien - mehrere zunehmend größer aufgeblasene Boxen übereinander simulieren
            // eine deutlich dickere Linie (Creates Catnip-Outliner kann echte Linienbreite, uns
            // steht nur die einfache Vanilla-Hilfsmethode zur Verfügung, siehe Klassenkommentar).
            for (double inflate : LINE_LAYER_INFLATE) {
                AABB layer = new AABB(pos).inflate(inflate);
                LevelRenderer.renderLineBox(poseStack, vertexConsumer, layer, color[0], color[1], color[2], 1f);
            }
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static String tunedGroupIdOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof SmartObserverBlock)) return null;
        ContentObserverGroupData tuning = stack.get(ContentObserverDataComponents.GROUP_TUNING.get());
        return tuning != null ? tuning.groupId() : null;
    }
}
