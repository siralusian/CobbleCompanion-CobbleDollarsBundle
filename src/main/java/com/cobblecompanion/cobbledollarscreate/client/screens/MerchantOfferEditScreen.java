package com.cobblecompanion.cobbledollarscreate.client.screens;

import com.cobblecompanion.client.data.ClientNetworkUtil;
import com.cobblecompanion.client.screens.FixedScaleScreen;
import com.cobblecompanion.cobbledollarscreate.network.MerchantOfferUpdatePacket;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Individuelles Verkaufs-Angebot eines einzelnen CobbleMerchant/CustomNPC-Traders bearbeiten
 * (siehe MerchantOfferManager) - geöffnet über "Angebot bearbeiten" im Verknüpfte-NPCs-Panel von
 * StockTickerPriceScreen. Links das eigene (individuelle) Angebot, rechts das volle Angebot des
 * verknüpften Lagerticker-Netzwerks - "+" übernimmt das rechts ausgewählte Item nach links, "-"
 * entfernt das links ausgewählte Item wieder. Die beiden Buttons oben schalten um, ob der Merchant
 * gerade sein eigenes (links) oder das volle Netzwerk-Angebot (rechts, Standardverhalten) zeigt -
 * unabhängig davon bleibt die linke Liste bearbeitbar, damit sie sich vorbereiten lässt, bevor man
 * umschaltet. Jede Änderung wird sofort gespeichert (kein separater Speichern-Button, wie
 * StockTickerPriceScreens "Übernehmen").
 *
 * Nutzer-Vorgabe: das Netzwerk-Angebot (rechte Liste) UND die Kategorie-Zuordnung kommen bewusst
 * NICHT über eine eigene Server-Anfrage, sondern direkt vom aufrufenden StockTickerPriceScreen
 * (siehe ClientMerchantOfferHelper.requestEdit) - der geöffnete Preis-Editor hat diese Daten
 * (Preisliste des aktuellen Netzwerks) ohnehin bereits vollständig geladen, ein Merchant taucht im
 * Verknüpfte-NPCs-Panel nur auf, wenn sein Ticker zum selben Netzwerk gehört (siehe
 * CreateStockTickerInteractionHandler.collectLinkedMerchants). Nur der individuelle
 * Angebots-Zustand selbst (aktiv/Item-Liste) kommt per MerchantOfferRequestPacket vom Server, da er
 * sonst nirgends im Client bekannt ist.
 */
public class MerchantOfferEditScreen extends FixedScaleScreen {

    private static void sendToServer(CustomPacketPayload payload) {
        if (ClientNetworkUtil.canSendToServerOrWarn(payload.type().id())) {
            PacketDistributor.sendToServer(payload);
        }
    }

    private static final int TOP_Y = 20;
    private static final int SEARCH_BOX_W = 260;
    private static final int SEARCH_BOX_H = 16;
    private static final int SEARCH_TO_MODE_GAP_Y = 10;
    private static final int MODE_BTN_W = 140;
    private static final int MODE_BTN_H = 18;
    private static final int MODE_BTN_GAP_X = 6;
    private static final int MODE_TO_LIST_GAP_Y = 12;
    private static final int LIST_LABEL_GAP_Y = 11;
    private static final int DROPDOWN_BTN_W = 64;
    private static final int DROPDOWN_BTN_H = 16;
    private static final int DROPDOWN_GAP_X = 4;
    private static final int LIST_W = 200;
    private static final int LIST_H = 210;
    private static final int CENTER_COL_W = 22;
    private static final int LIST_TO_CENTER_GAP_X = 6;
    private static final int ROW_H = 18;
    private static final int ICON_SIZE = 16;
    private static final int NAME_GAP_X = 20;
    private static final int TRANSFER_BTN_SIZE = 20;
    private static final int TRANSFER_BTN_GAP_Y = 8;
    private static final int SCROLLBAR_W = 4;
    private static final int SUGGEST_ROW_H = 14;
    private static final int BOTTOM_MARGIN = 16;
    private static final int DONE_BTN_W = 90;
    private static final int DONE_BTN_H = 20;
    private static final int DONE_GAP_Y = 14;

    private final BlockPos tickerPos;
    private final UUID merchantUuid;
    private final String merchantName;
    private final Map<String, String> categoriesByItemId;

    private boolean active;
    private final List<String> customIds;
    private final List<String> networkIds;

    private EditBox searchBox;
    private Button ownOfferButton;
    private Button networkOfferButton;
    private Button leftCategoryButton;
    private Button rightCategoryButton;
    private boolean leftCategoryDropdownOpen = false;
    private boolean rightCategoryDropdownOpen = false;
    /** null = alle, "" = ohne Kategorie, sonst konkreter Kategoriename. */
    private String leftCategoryFilter = null;
    private String rightCategoryFilter = null;

    private String selectedLeftId = null;
    private String selectedRightId = null;
    private double leftScroll = 0;
    private double rightScroll = 0;
    private List<String> leftFiltered = List.of();
    private List<String> rightFiltered = List.of();

    private int leftListX;
    private int rightListX;
    private int leftDropdownX;
    private int rightDropdownX;
    private int listTopY;
    private int addButtonY;
    private int removeButtonY;
    private int centerX;

    public MerchantOfferEditScreen(BlockPos tickerPos, UUID merchantUuid, String merchantName, boolean active,
            List<String> customOfferItemIds, List<String> networkOfferItemIds, Map<String, String> categoriesByItemId) {
        super(Component.translatable("cobblecompanion.gui.merchantoffer.title", merchantName));
        this.tickerPos = tickerPos;
        this.merchantUuid = merchantUuid;
        this.merchantName = merchantName;
        this.active = active;
        this.customIds = new ArrayList<>(new LinkedHashSet<>(customOfferItemIds));
        this.networkIds = new ArrayList<>(new LinkedHashSet<>(networkOfferItemIds));
        this.categoriesByItemId = categoriesByItemId;
    }

    @Override
    protected void initScaled() {
        int totalWidth = DROPDOWN_BTN_W + DROPDOWN_GAP_X + LIST_W + LIST_TO_CENTER_GAP_X + CENTER_COL_W
            + LIST_TO_CENTER_GAP_X + LIST_W + DROPDOWN_GAP_X + DROPDOWN_BTN_W;
        int startX = (this.width - totalWidth) / 2;

        leftDropdownX = startX;
        leftListX = leftDropdownX + DROPDOWN_BTN_W + DROPDOWN_GAP_X;
        centerX = leftListX + LIST_W + LIST_TO_CENTER_GAP_X;
        rightListX = centerX + CENTER_COL_W + LIST_TO_CENTER_GAP_X;
        rightDropdownX = rightListX + LIST_W + DROPDOWN_GAP_X;

        int searchX = (this.width - SEARCH_BOX_W) / 2;
        searchBox = new EditBox(this.font, searchX, TOP_Y, SEARCH_BOX_W, SEARCH_BOX_H,
            Component.translatable("cobblecompanion.gui.merchantoffer.search_hint"));
        searchBox.setHint(Component.translatable("cobblecompanion.gui.merchantoffer.search_hint"));
        searchBox.setMaxLength(256);
        searchBox.setResponder(s -> rebuildFiltered());
        addRenderableWidget(searchBox);

        int modeY = TOP_Y + SEARCH_BOX_H + SEARCH_TO_MODE_GAP_Y;
        int modeX = (this.width - (MODE_BTN_W * 2 + MODE_BTN_GAP_X)) / 2;
        ownOfferButton = Button.builder(Component.translatable("cobblecompanion.gui.merchantoffer.own_offer"), b -> setActive(true))
            .bounds(modeX, modeY, MODE_BTN_W, MODE_BTN_H)
            .build();
        addRenderableWidget(ownOfferButton);
        networkOfferButton = Button.builder(Component.translatable("cobblecompanion.gui.merchantoffer.network_offer"), b -> setActive(false))
            .bounds(modeX + MODE_BTN_W + MODE_BTN_GAP_X, modeY, MODE_BTN_W, MODE_BTN_H)
            .build();
        addRenderableWidget(networkOfferButton);
        updateModeButtons();

        listTopY = modeY + MODE_BTN_H + MODE_TO_LIST_GAP_Y + LIST_LABEL_GAP_Y;

        leftCategoryButton = Button.builder(leftCategoryLabel(), b -> leftCategoryDropdownOpen = !leftCategoryDropdownOpen)
            .bounds(leftDropdownX, listTopY, DROPDOWN_BTN_W, DROPDOWN_BTN_H)
            .build();
        addRenderableWidget(leftCategoryButton);
        rightCategoryButton = Button.builder(rightCategoryLabel(), b -> rightCategoryDropdownOpen = !rightCategoryDropdownOpen)
            .bounds(rightDropdownX, listTopY, DROPDOWN_BTN_W, DROPDOWN_BTN_H)
            .build();
        addRenderableWidget(rightCategoryButton);

        int transferColCenterY = listTopY + LIST_H / 2;
        addButtonY = transferColCenterY - TRANSFER_BTN_SIZE - TRANSFER_BTN_GAP_Y / 2;
        removeButtonY = transferColCenterY + TRANSFER_BTN_GAP_Y / 2;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> addSelectedToCustom())
            .bounds(centerX, addButtonY, CENTER_COL_W, TRANSFER_BTN_SIZE)
            .build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> removeSelectedFromCustom())
            .bounds(centerX, removeButtonY, CENTER_COL_W, TRANSFER_BTN_SIZE)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("cobblecompanion.gui.merchantoffer.done"), b -> onClose())
            .bounds((this.width - DONE_BTN_W) / 2, listTopY + LIST_H + DONE_GAP_Y, DONE_BTN_W, DONE_BTN_H)
            .build());

        rebuildFiltered();
    }

    private void updateModeButtons() {
        ownOfferButton.setMessage(Component.translatable(active
            ? "cobblecompanion.gui.merchantoffer.own_offer_active"
            : "cobblecompanion.gui.merchantoffer.own_offer"));
        networkOfferButton.setMessage(Component.translatable(!active
            ? "cobblecompanion.gui.merchantoffer.network_offer_active"
            : "cobblecompanion.gui.merchantoffer.network_offer"));
    }

    private void setActive(boolean value) {
        if (active == value) return;
        active = value;
        updateModeButtons();
        sendUpdate();
    }

    private void addSelectedToCustom() {
        if (selectedRightId == null) return;
        if (!customIds.contains(selectedRightId)) {
            customIds.add(selectedRightId);
            sendUpdate();
            rebuildFiltered();
        }
    }

    private void removeSelectedFromCustom() {
        if (selectedLeftId == null) return;
        customIds.remove(selectedLeftId);
        selectedLeftId = null;
        sendUpdate();
        rebuildFiltered();
    }

    private void sendUpdate() {
        sendToServer(new MerchantOfferUpdatePacket(merchantUuid, active, new ArrayList<>(customIds)));
    }

    private String displayNameOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
        return item != null ? Component.translatable(item.getDescriptionId()).getString() : id;
    }

    private void rebuildFiltered() {
        String search = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        leftFiltered = filterList(customIds, leftCategoryFilter, search);
        rightFiltered = filterList(networkIds, rightCategoryFilter, search);
        leftScroll = clampScroll(leftScroll, leftFiltered.size());
        rightScroll = clampScroll(rightScroll, rightFiltered.size());
    }

    private double clampScroll(double scroll, int rowCount) {
        return Math.max(0, Math.min(scroll, Math.max(0, rowCount * ROW_H - LIST_H)));
    }

    private List<String> filterList(List<String> source, String categoryFilter, String search) {
        List<String> result = new ArrayList<>();
        for (String id : source) {
            if (!search.isEmpty() && !id.toLowerCase().contains(search) && !displayNameOf(id).toLowerCase().contains(search)) continue;
            if (categoryFilter != null) {
                String category = categoriesByItemId.get(id);
                boolean hasCategory = category != null && !category.isBlank();
                if (categoryFilter.isEmpty()) {
                    if (hasCategory) continue;
                } else if (!categoryFilter.equals(category)) {
                    continue;
                }
            }
            result.add(id);
        }
        result.sort(Comparator.comparing(this::displayNameOf, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** Alle bekannten Kategorienamen der Netzwerk-Preisliste (dedupliziert, sortiert) - für beide Kategorie-Dropdowns. */
    private List<String> knownCategories() {
        var categories = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (String category : categoriesByItemId.values()) {
            if (category != null && !category.isBlank()) categories.add(category);
        }
        return new ArrayList<>(categories);
    }

    private Component leftCategoryLabel() {
        return categoryButtonLabel(leftCategoryFilter);
    }

    private Component rightCategoryLabel() {
        return categoryButtonLabel(rightCategoryFilter);
    }

    private Component categoryButtonLabel(String filter) {
        if (filter == null) return Component.translatable("cobblecompanion.gui.merchantoffer.category");
        if (filter.isEmpty()) return Component.translatable("cobblecompanion.gui.merchantoffer.category_none");
        return Component.literal(filter);
    }

    /** Dropdown-Optionen: "Alle" (Filter aus), "Ohne Kategorie", dann alle bekannten Kategorien. */
    private List<String> categoryDropdownOptions() {
        List<String> options = new ArrayList<>();
        options.add(Component.translatable("cobblecompanion.gui.merchantoffer.category_all").getString());
        options.add(Component.translatable("cobblecompanion.gui.merchantoffer.category_none").getString());
        options.addAll(knownCategories());
        return options;
    }

    private void chooseCategory(boolean left, int index) {
        String value;
        if (index == 0) value = null;
        else if (index == 1) value = "";
        else value = knownCategories().get(index - 2);

        if (left) {
            leftCategoryFilter = value;
            leftCategoryDropdownOpen = false;
            leftCategoryButton.setMessage(leftCategoryLabel());
        } else {
            rightCategoryFilter = value;
            rightCategoryDropdownOpen = false;
            rightCategoryButton.setMessage(rightCategoryLabel());
        }
        rebuildFiltered();
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

    private void renderList(GuiGraphics graphics, int x, List<String> ids, String selectedId, double scroll, int mouseX, int mouseY) {
        graphics.fill(x, listTopY, x + LIST_W, listTopY + LIST_H, 0x80000000);
        graphics.enableScissor(toRealX(x), toRealY(listTopY), toRealX(x + LIST_W), toRealY(listTopY + LIST_H));
        int y = listTopY - (int) scroll;
        for (String id : ids) {
            if (y + ROW_H >= listTopY && y <= listTopY + LIST_H) {
                boolean hovered = mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + ROW_H;
                boolean selected = id.equals(selectedId);
                if (selected) graphics.fill(x, y, x + LIST_W, y + ROW_H, 0x8000AAFF);
                else if (hovered) graphics.fill(x, y, x + LIST_W, y + ROW_H, 0x40FFFFFF);

                ItemStack stack = itemStackOf(id);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, x + 2, y + (ROW_H - ICON_SIZE) / 2);
                }
                String name = displayNameOf(id);
                int maxTextWidth = LIST_W - NAME_GAP_X - 4 - SCROLLBAR_W;
                if (this.font.width(name) > maxTextWidth) {
                    name = this.font.plainSubstrByWidth(name, Math.max(0, maxTextWidth - this.font.width("..."))) + "...";
                }
                graphics.drawString(this.font, name, x + NAME_GAP_X, y + (ROW_H - this.font.lineHeight) / 2, 0xFFFFFF, false);
            }
            y += ROW_H;
        }
        graphics.disableScissor();

        int maxScroll = Math.max(0, ids.size() * ROW_H - LIST_H);
        if (maxScroll > 0) {
            int barX = x + LIST_W - SCROLLBAR_W;
            int trackHeight = LIST_H;
            int barHeight = Math.max(10, LIST_H * LIST_H / (ids.size() * ROW_H));
            int barY = listTopY + (int) ((trackHeight - barHeight) * (scroll / maxScroll));
            graphics.fill(barX, listTopY, barX + SCROLLBAR_W, listTopY + trackHeight, 0x40FFFFFF);
            graphics.fill(barX, barY, barX + SCROLLBAR_W, barY + barHeight, 0xFFC0C0C0);
        }
    }

    private ItemStack itemStackOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        renderWidgets(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);

        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.merchantoffer.own_list_label"),
            leftListX, listTopY - LIST_LABEL_GAP_Y, 0xA0A0A0);
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.merchantoffer.network_list_label"),
            rightListX, listTopY - LIST_LABEL_GAP_Y, 0xA0A0A0);

        renderList(graphics, leftListX, leftFiltered, selectedLeftId, leftScroll, mouseX, mouseY);
        renderList(graphics, rightListX, rightFiltered, selectedRightId, rightScroll, mouseX, mouseY);

        if (leftCategoryDropdownOpen) {
            renderSuggestions(graphics, mouseX, mouseY, leftCategoryButton.getX(), leftCategoryButton.getY() + DROPDOWN_BTN_H,
                Math.max(DROPDOWN_BTN_W, 90), categoryDropdownOptions());
        }
        if (rightCategoryDropdownOpen) {
            renderSuggestions(graphics, mouseX, mouseY, rightCategoryButton.getX(), rightCategoryButton.getY() + DROPDOWN_BTN_H,
                Math.max(DROPDOWN_BTN_W, 90), categoryDropdownOptions());
        }
    }

    private boolean handleDropdownClick(double mouseX, double mouseY, boolean left) {
        Button button = left ? leftCategoryButton : rightCategoryButton;
        List<String> options = categoryDropdownOptions();
        int x = button.getX();
        int y = button.getY() + DROPDOWN_BTN_H;
        int w = Math.max(DROPDOWN_BTN_W, 90);
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + options.size() * SUGGEST_ROW_H) {
            int index = (int) ((mouseY - y) / SUGGEST_ROW_H);
            if (index >= 0 && index < options.size()) chooseCategory(left, index);
            return true;
        }
        if (left) leftCategoryDropdownOpen = false; else rightCategoryDropdownOpen = false;
        return true;
    }

    private boolean handleListClick(double mouseX, double mouseY, int x, List<String> ids, double scroll, boolean left) {
        if (mouseX < x || mouseX >= x + LIST_W || mouseY < listTopY || mouseY >= listTopY + LIST_H) return false;
        int index = (int) ((mouseY - listTopY + scroll) / ROW_H);
        if (index < 0 || index >= ids.size()) return true;
        if (left) selectedLeftId = ids.get(index);
        else selectedRightId = ids.get(index);
        return true;
    }

    @Override
    protected boolean mouseClickedScaled(double mouseX, double mouseY, int button) {
        if (leftCategoryDropdownOpen) return handleDropdownClick(mouseX, mouseY, true);
        if (rightCategoryDropdownOpen) return handleDropdownClick(mouseX, mouseY, false);

        if (handleListClick(mouseX, mouseY, leftListX, leftFiltered, leftScroll, true)) return true;
        if (handleListClick(mouseX, mouseY, rightListX, rightFiltered, rightScroll, false)) return true;

        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    @Override
    protected boolean mouseScrolledScaled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftListX && mouseX < leftListX + LIST_W && mouseY >= listTopY && mouseY < listTopY + LIST_H) {
            leftScroll = clampScroll(leftScroll - scrollY * ROW_H, leftFiltered.size());
            return true;
        }
        if (mouseX >= rightListX && mouseX < rightListX + LIST_W && mouseY >= listTopY && mouseY < listTopY + LIST_H) {
            rightScroll = clampScroll(rightScroll - scrollY * ROW_H, rightFiltered.size());
            return true;
        }
        return super.mouseScrolledScaled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
