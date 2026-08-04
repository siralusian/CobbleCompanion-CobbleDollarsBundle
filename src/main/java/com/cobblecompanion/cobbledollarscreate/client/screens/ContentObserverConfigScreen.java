package com.cobblecompanion.cobbledollarscreate.client.screens;

import com.cobblecompanion.client.data.ClientNetworkUtil;
import com.cobblecompanion.client.screens.FixedScaleScreen;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientKnownPlayersHelper;
import com.cobblecompanion.cobbledollarscreate.network.ContentObserverConfigUpdatePacket;
import com.cobblecompanion.cobbledollarscreate.network.KnownPlayersSyncPacket;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor für den "Schlauen Beobachter" (create:content_observer), geöffnet per Strg+Rechtsklick
 * (echter Minecraft-OP, siehe ContentObserverInteractionHandler). Bewusst schlank (nur 3 Felder)
 * statt des großen Item-Listen-Editors wie StockTickerPriceScreen - hier gibt es nur genau EIN
 * Item-Muster pro Block, kein Sinn in einer durchsuchbaren Liste.
 *
 * Nutzer-Vorgabe: Item- und Zielspieler-Feld bekommen dieselbe Vorschlagsfunktion wie im
 * Lagerticker-Preis-Editor - ABER rechts neben dem jeweiligen Feld statt darunter (Nutzer-Fund: die
 * Vorschlagsbox darunter verdeckte sonst das nächste Feld, man musste von unten nach oben
 * ausfüllen). Zusätzlich Spieler-Inventar zum Anklicken (wie im Lagerticker-Preis-Editor) sowie
 * Item-Muster-Eingabe, die neben einer konkreten Item-ID auch leer/"*" (alles), "minecraft:*"
 * (Namespace) und "#minecraft:logs" (Tag) akzeptiert (siehe ContentObserverConfigManager.matches).
 */
public class ContentObserverConfigScreen extends FixedScaleScreen {

    /**
     * Zentrale Sende-Stelle für Client->Server-Pakete dieses Screens (dieselbe Absicherung wie
     * CompanionScreen.sendToServer, siehe dessen Klassenkommentar): dieser Screen öffnet sich nur
     * NACH einem vorherigen S2C-Sync (ContentObserverConfigScreen wird über einen Versionszähler
     * in ClientEventHandler geöffnet, siehe dessen Kommentar) - der Server hat also GRUNDSÄTZLICH
     * eine kompatible CobbleCompanion-Version. Trotzdem wird jeder einzelne Kanal unabhängig
     * ausgehandelt (siehe ClientNetworkUtil-Klassenkommentar) - ein Versions-Unterschied zwischen
     * dem Sync-Kanal und diesem Speichern-Kanal ist deshalb theoretisch möglich und würde sonst
     * eine UnsupportedOperationException werfen und den Client crashen.
     */
    private static void sendToServer(CustomPacketPayload payload) {
        if (ClientNetworkUtil.canSendToServerOrWarn(payload.type().id())) {
            PacketDistributor.sendToServer(payload);
        }
    }

    private static final int PANEL_WIDTH = 260;
    private static final int TOP_Y = 50;
    private static final int ROW_H = 22;
    private static final int LABEL_GAP_Y = 12;
    private static final int FIELD_H = 18;
    private static final int BTN_W = 80;
    private static final int BTN_H = 20;
    private static final int BTN_GAP_X = 8;
    private static final int BTN_GAP_Y = 20;
    private static final int HINT_GAP_Y = 14;
    private static final int SUGGEST_ROW_H = 14;
    private static final int MAX_ITEMID_SUGGESTIONS = 6;
    private static final int MAX_PLAYER_SUGGESTIONS = 8;
    /** Nutzer-Vorgabe: Vorschlagsbox rechts NEBEN dem Feld statt darunter (verdeckt sonst das nächste Feld). */
    private static final int SUGGEST_GAP_X = 6;
    private static final int SUGGEST_BOX_W = 170;

    private static final int INV_TO_HINT_GAP_Y = 18;
    private static final int INV_SLOT_SIZE = 18;
    private static final int INV_COLUMNS = 9;
    private static final int INV_MAIN_ROWS = 3;
    private static final int INV_ROW_GAP_Y = 4;
    private static final int INV_LABEL_GAP_Y = 12;

    private final BlockPos pos;
    private final String initialItemId;
    private final String initialTargetPlayerName;
    private final long initialAmount;

    private EditBox itemIdBox;
    private EditBox targetPlayerBox;
    private EditBox amountBox;

    private int invTop;
    private int invX;
    private int hotbarY;

    public ContentObserverConfigScreen(BlockPos pos, String itemId, String targetPlayerName, long amountPerItem) {
        super(Component.translatable("cobblecompanion.gui.contentobserver.title"));
        this.pos = pos;
        this.initialItemId = itemId;
        this.initialTargetPlayerName = targetPlayerName;
        this.initialAmount = amountPerItem;
    }

    @Override
    protected void initScaled() {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int y = TOP_Y;

        itemIdBox = new EditBox(this.font, panelX, y + LABEL_GAP_Y, PANEL_WIDTH, FIELD_H,
            Component.translatable("cobblecompanion.gui.contentobserver.item_hint"));
        itemIdBox.setHint(Component.translatable("cobblecompanion.gui.contentobserver.item_hint"));
        itemIdBox.setMaxLength(256);
        itemIdBox.setValue(initialItemId);
        addRenderableWidget(itemIdBox);
        y += ROW_H + LABEL_GAP_Y;

        targetPlayerBox = new EditBox(this.font, panelX, y + LABEL_GAP_Y, PANEL_WIDTH, FIELD_H,
            Component.translatable("cobblecompanion.gui.contentobserver.player_hint"));
        targetPlayerBox.setHint(Component.translatable("cobblecompanion.gui.contentobserver.player_hint"));
        targetPlayerBox.setMaxLength(64);
        targetPlayerBox.setValue(initialTargetPlayerName);
        addRenderableWidget(targetPlayerBox);
        y += ROW_H + LABEL_GAP_Y;

        amountBox = new EditBox(this.font, panelX, y + LABEL_GAP_Y, PANEL_WIDTH, FIELD_H,
            Component.translatable("cobblecompanion.gui.contentobserver.amount_hint"));
        amountBox.setMaxLength(15);
        amountBox.setValue(initialAmount != 0 ? CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(initialAmount)) : "");
        addRenderableWidget(amountBox);
        y += ROW_H + LABEL_GAP_Y + HINT_GAP_Y;

        // Nutzer-Vorgabe: eigenes Inventar zum Anklicken, wie im Lagerticker-Preis-Editor
        // (StockTickerPriceScreen) - übernimmt das angeklickte Item in itemIdBox.
        invTop = y + INV_LABEL_GAP_Y;
        invX = (this.width - INV_SLOT_SIZE * INV_COLUMNS) / 2;
        hotbarY = invTop + INV_MAIN_ROWS * INV_SLOT_SIZE + INV_ROW_GAP_Y;
        y = hotbarY + INV_SLOT_SIZE + INV_TO_HINT_GAP_Y;

        int btnY = y + BTN_GAP_Y;
        addRenderableWidget(Button.builder(Component.translatable("cobblecompanion.gui.contentobserver.save"), b -> save())
            .bounds(panelX, btnY, BTN_W, BTN_H)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("cobblecompanion.gui.contentobserver.clear"), b -> clear())
            .bounds(panelX + BTN_W + BTN_GAP_X, btnY, BTN_W, BTN_H)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(panelX + PANEL_WIDTH - BTN_W, btnY, BTN_W, BTN_H)
            .build());
    }

    /** Nutzer-Vorgabe: itemId darf jetzt leer bleiben (bedeutet "beobachte alles", siehe ContentObserverConfigManager.matches) - kein Early-Return mehr bei leerem Feld. */
    private void save() {
        java.math.BigInteger parsedAmount = CobbleDollarsScale.parseToRaw(amountBox.getValue().trim());
        long amount = parsedAmount != null ? parsedAmount.longValueExact() : 0;
        sendToServer(new ContentObserverConfigUpdatePacket(pos, false, itemIdBox.getValue().trim(), targetPlayerBox.getValue().trim(), amount));
        onClose();
    }

    private void clear() {
        sendToServer(new ContentObserverConfigUpdatePacket(pos, true, "", "", 0));
        onClose();
    }

    private void selectInventorySlot(int slotIndex) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getInventory().getItem(slotIndex);
        if (stack.isEmpty()) return;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        itemIdBox.setValue(id);
        itemIdBox.setCursorPosition(id.length());
    }

    /** Bis zu MAX_ITEMID_SUGGESTIONS Item-IDs aus der kompletten Registry, gefiltert nach ID ODER lokalisiertem Namen (aktuelle Client-Sprache) - wie StockTickerPriceScreen.itemIdSuggestions(). */
    private List<String> itemIdSuggestions() {
        String typed = itemIdBox.getValue().trim().toLowerCase();
        if (typed.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            String id = rl.toString();
            boolean matches = id.toLowerCase().contains(typed)
                || Component.translatable(item.getDescriptionId()).getString().toLowerCase().contains(typed);
            if (matches) {
                result.add(id);
                if (result.size() >= MAX_ITEMID_SUGGESTIONS) break;
            }
        }
        return result;
    }

    /** Bis zu MAX_PLAYER_SUGGESTIONS bekannte Spieler (siehe ClientKnownPlayersHelper), gefiltert nach dem Tipptext - wie der Verkaufserlöse-Empfänger-Picker in StockTickerPriceScreen. */
    private List<String> playerSuggestions() {
        String typed = targetPlayerBox.getValue().trim().toLowerCase();
        List<String> result = new ArrayList<>();
        for (KnownPlayersSyncPacket.Entry entry : ClientKnownPlayersHelper.getPlayers()) {
            if (typed.isEmpty() || entry.name().toLowerCase().contains(typed)) {
                result.add(entry.name());
                if (result.size() >= MAX_PLAYER_SUGGESTIONS) break;
            }
        }
        return result;
    }

    private void renderSuggestions(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, List<String> suggestions) {
        if (suggestions.isEmpty()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(x, y, x + w, y + suggestions.size() * SUGGEST_ROW_H, 0xE0000000);
        int rowY = y;
        for (String s : suggestions) {
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + SUGGEST_ROW_H;
            if (hovered) graphics.fill(x, rowY, x + w, rowY + SUGGEST_ROW_H, 0x40FFFFFF);
            graphics.drawString(this.font, s, x + 4, rowY + 3, 0xFFFFFF, false);
            rowY += SUGGEST_ROW_H;
        }
        graphics.pose().popPose();
    }

    /** X-Koordinate der Vorschlagsbox eines Feldes - rechts daneben, siehe Klassenkommentar. */
    private int suggestX(EditBox box) {
        return box.getX() + box.getWidth() + SUGGEST_GAP_X;
    }

    private void renderInventorySlotIcon(GuiGraphics graphics, net.minecraft.world.entity.player.Player player, int slotIndex, int x, int y) {
        ItemStack stack = player.getInventory().getItem(slotIndex);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
        }
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int y = TOP_Y;
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.item_label"), panelX, y, 0xA0A0A0);
        y += ROW_H + LABEL_GAP_Y;
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.player_label"), panelX, y, 0xA0A0A0);
        y += ROW_H + LABEL_GAP_Y;
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.amount_label"), panelX, y, 0xA0A0A0);
        y += ROW_H + LABEL_GAP_Y + HINT_GAP_Y;

        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (int row = 0; row < INV_MAIN_ROWS; row++) {
                int rowY = invTop + row * INV_SLOT_SIZE;
                for (int col = 0; col < INV_COLUMNS; col++) {
                    int slotIndex = 9 + row * INV_COLUMNS + col;
                    renderInventorySlotIcon(graphics, player, slotIndex, invX + col * INV_SLOT_SIZE, rowY);
                }
            }
            for (int i = 0; i < INV_COLUMNS; i++) {
                renderInventorySlotIcon(graphics, player, i, invX + i * INV_SLOT_SIZE, hotbarY);
            }
        }
        y = hotbarY + INV_SLOT_SIZE + INV_TO_HINT_GAP_Y;
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.hint"), panelX, y, 0x808080);

        // Autocomplete-Boxen: nur solange das jeweilige Feld fokussiert ist (sonst würden sie
        // permanent über der restlichen GUI schweben) - rechts neben dem Feld (siehe Klassenkommentar).
        if (itemIdBox.isFocused()) {
            renderSuggestions(graphics, mouseX, mouseY, suggestX(itemIdBox), itemIdBox.getY(), SUGGEST_BOX_W, itemIdSuggestions());
        }
        if (targetPlayerBox.isFocused()) {
            renderSuggestions(graphics, mouseX, mouseY, suggestX(targetPlayerBox), targetPlayerBox.getY(), SUGGEST_BOX_W, playerSuggestions());
        }
    }

    @Override
    protected boolean mouseClickedScaled(double mouseX, double mouseY, int button) {
        if (itemIdBox.isFocused()) {
            List<String> suggestions = itemIdSuggestions();
            int x = suggestX(itemIdBox);
            int y = itemIdBox.getY();
            if (!suggestions.isEmpty() && mouseX >= x && mouseX < x + SUGGEST_BOX_W && mouseY >= y && mouseY < y + suggestions.size() * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - y) / SUGGEST_ROW_H);
                if (index >= 0 && index < suggestions.size()) {
                    itemIdBox.setValue(suggestions.get(index));
                    itemIdBox.setCursorPosition(suggestions.get(index).length());
                    return true;
                }
            }
        }

        if (targetPlayerBox.isFocused()) {
            List<String> suggestions = playerSuggestions();
            int x = suggestX(targetPlayerBox);
            int y = targetPlayerBox.getY();
            if (!suggestions.isEmpty() && mouseX >= x && mouseX < x + SUGGEST_BOX_W && mouseY >= y && mouseY < y + suggestions.size() * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - y) / SUGGEST_ROW_H);
                if (index >= 0 && index < suggestions.size()) {
                    targetPlayerBox.setValue(suggestions.get(index));
                    targetPlayerBox.setCursorPosition(suggestions.get(index).length());
                    return true;
                }
            }
        }

        if (mouseX >= invX && mouseX < invX + INV_SLOT_SIZE * INV_COLUMNS) {
            if (mouseY >= invTop && mouseY < invTop + INV_MAIN_ROWS * INV_SLOT_SIZE) {
                int col = (int) ((mouseX - invX) / INV_SLOT_SIZE);
                int row = (int) ((mouseY - invTop) / INV_SLOT_SIZE);
                selectInventorySlot(9 + row * INV_COLUMNS + col);
                return true;
            }
            if (mouseY >= hotbarY && mouseY < hotbarY + INV_SLOT_SIZE) {
                int col = (int) ((mouseX - invX) / INV_SLOT_SIZE);
                selectInventorySlot(col);
                return true;
            }
        }

        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
