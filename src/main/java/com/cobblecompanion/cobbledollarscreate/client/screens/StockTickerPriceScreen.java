package com.cobblecompanion.cobbledollarscreate.client.screens;

import com.cobblecompanion.client.data.ClientNetworkUtil;
import com.cobblecompanion.client.screens.FixedScaleScreen;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientKnownPlayersHelper;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientLinkedMerchantsHelper;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientMerchantOfferHelper;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientSaleRecipientHelper;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantPayoutManager;
import com.cobblecompanion.cobbledollarscreate.network.CreateStockTickerPricesUpdatePacket;
import com.cobblecompanion.cobbledollarscreate.network.KnownPlayersSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.LinkedMerchantActionPacket;
import com.cobblecompanion.cobbledollarscreate.network.LinkedMerchantInfo;
import com.cobblecompanion.cobbledollarscreate.network.MerchantOfferRequestPacket;
import com.cobblecompanion.cobbledollarscreate.network.PriceListPayload;
import com.cobblecompanion.cobbledollarscreate.network.SaleRecipientEntityUpdatePacket;
import com.cobblecompanion.cobbledollarscreate.network.SaleRecipientNetworkUpdatePacket;
import com.cobblecompanion.cobbledollarscreate.network.SaleRecipientSyncPacket;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Preis-Editor für die Preislisten (siehe CentralItemPriceManager - mehrere benannte Listen,
 * wählbar pro Lagerticker-Netzwerk), geöffnet per Strg+Rechtsklick auf einen Lagerticker (siehe
 * CreateStockTickerInteractionHandler). Bewusst als eigenständiger Screen statt Erweiterung von
 * Creates StockKeeperRequestScreen - vermeidet Zugriff auf dessen private Felder, robuster
 * gegenüber Create-Updates.
 *
 * Zeigt eine such-/sortierbare Liste aller aktuell im angeschlossenen Logistiknetzwerk verfügbaren
 * Items (Icon + Name, siehe CreateStockTickerBridge.getAvailableItemIds) PLUS aller bereits
 * bepreisten Items (auch wenn gerade nicht verfügbar, damit ihr Preis nicht beim Speichern
 * verloren geht). Ein Klick auf eine Zeile lädt sie in die "Basis"-Eingabe unten; zusätzlich kann
 * dort eine beliebige Item-ID von Hand eingegeben werden (mit Autocomplete über die komplette
 * Item-Registry, nach ID ODER lokalisiertem Namen), oder per Klick auf einen der 36 Slots des
 * eigenen Inventars das dort gehaltene Item übernommen werden.
 *
 * Links neben dem zentrierten Panel (wenn genug Platz ist): Übersicht aller CustomNPC-Trader/
 * CobbleMerchants, die an dasselbe Lagerticker-Netzwerk angeschlossen sind (siehe
 * LinkedMerchantsSyncPacket/ClientLinkedMerchantsHelper) - Klick auf einen Eintrag öffnet ein
 * Popup zum Trennen der Kisten-/Ticker-Verknüpfung oder zum Löschen (mit Bestätigung).
 *
 * Nutzer-Vorgabe: das gesamte Menü bekommt einen automatisch erscheinenden Seiten-Scroll (nur bei
 * GUI-Skalierungen, bei denen nicht alles ins Fenster passt) - alle über addRenderableWidget
 * erzeugten Widgets werden zusätzlich mit ihrer "natürlichen" (unverschobenen) Y-Position gemerkt
 * (siehe trackedWidgets/applyPageScroll) und bei jeder Scroll-Änderung per AbstractWidget.setY neu
 * positioniert. Passt bereits alles ins Fenster, bleibt maxPageScroll 0 und nichts verschiebt sich.
 */
public class StockTickerPriceScreen extends FixedScaleScreen {

    /** Zentrale Sende-Stelle für Client->Server-Pakete dieses Screens - siehe ContentObserverConfigScreen.sendToServer-Kommentar. */
    private static void sendToServer(CustomPacketPayload payload) {
        if (ClientNetworkUtil.canSendToServerOrWarn(payload.type().id())) {
            PacketDistributor.sendToServer(payload);
        }
    }

    private static final int PANEL_WIDTH = 320;
    private static final int TOP_Y = 16;
    private static final int TOGGLE_BTN_W = 150;
    private static final int TOGGLE_BTN_H = 16;
    private static final int LIST_ROW_GAP_Y = 8;
    private static final int LIST_SELECT_BTN_W = 210;
    private static final int LIST_ROW_H = 16;
    private static final int LIST_DELETE_BTN_W = 90;
    private static final int LIST_ROW_TO_CREATE_GAP_Y = 4;
    private static final int LIST_NAME_BOX_W = 210;
    private static final int LIST_CREATE_BTN_W = 90;
    private static final int EXPORT_IMPORT_BTN_W = 90;
    private static final int ROW_RESET_COL_W = 14;
    private static final String EXPORT_MARKER = "COBBLECOMPANION_PRICELIST_V1";
    private static final int LIST_TO_SORT_GAP_Y = 10;
    private static final int SORT_BTN_W = 110;
    private static final int SORT_DIR_BTN_W = 90;
    private static final int SORT_BTN_H = 14;
    private static final int SORT_BTN_GAP_X = 6;
    private static final int SORT_TO_SEARCH_GAP_Y = 8;
    private static final int SEARCH_BOX_H = 16;
    private static final int SEARCH_TO_LIST_GAP_Y = 10;
    private static final int LIST_HEIGHT = 160;
    private static final int ROW_H = 18;
    private static final int ICON_SIZE = 16;
    private static final int NAME_GAP_X = 20;
    private static final int PRICE_COLUMN_W = 100;
    private static final int LIST_TO_ENTRY_GAP_Y = 14;
    private static final int ENTRY_ROW_H = 16;
    private static final int ITEMID_BOX_W = 108;
    private static final int PRICE_BOX_W = 55;
    private static final int FIELD_GAP_X = 6;
    private static final int APPLY_BTN_W = 80;
    private static final int ENTRY_TO_CATEGORY_GAP_Y = 6;
    private static final int CATEGORY_BOX_W = 210;
    private static final int CATEGORY_TO_INV_GAP_Y = 16;
    private static final int INV_SLOT_SIZE = 18;
    private static final int INV_COLUMNS = 9;
    private static final int INV_MAIN_ROWS = 3; // Hauptinventar (Slots 9-35), Hotbar (0-8) kommt als 4. Reihe darunter
    private static final int INV_ROW_GAP_Y = 4; // kleine Lücke zwischen Hauptinventar und Hotbar, wie in Vanilla
    private static final int ACTION_BTN_W = 90;
    private static final int ACTION_BTN_H = 20;
    private static final int ACTION_GAP_Y = 18;
    private static final int BOTTOM_MARGIN = 16;
    private static final int SUGGEST_ROW_H = 14;
    private static final int MAX_CATEGORY_SUGGESTIONS = 5;
    private static final int MAX_ITEMID_SUGGESTIONS = 6;

    private static final int SCROLLBAR_W = 6;

    private static final int LEFT_PANEL_MARGIN = 8;
    private static final int LEFT_PANEL_MIN_WIDTH = 110;
    private static final int LEFT_ROW_H = 54;
    private static final int LEFT_PREVIEW_SIZE = 28;
    private static final int LEFT_TEXT_GAP_X = 4;
    private static final int ACTION_POPUP_W = 170;
    private static final int LEFT_RECIPIENT_LINE_Y = 38;

    private static final int RECIPIENT_ROW_H = 16;
    private static final int RECIPIENT_MODE_POPUP_W = 200;
    private static final int RIGHT_SALE_BUTTON_W = 150;
    private static final int FILTER_ROW_GAP_Y = 4;
    private static final int FILTER_STATE_TEXT_GAP_X = 6;
    private static final int DROPDOWN_RIGHT_GAP_X = 5;
    private static final int PLAYER_PICKER_BOX_W = 180;
    private static final int PLAYER_PICKER_BOX_H = 14;
    private static final int MAX_PLAYER_SUGGESTIONS = 8;

    private enum SortMode { NAME, ANKAUF, VERKAUF }

    /** sellPrice = Ankaufspreis (Merchant kauft dem Spieler ab), buyPrice = Verkaufspreis (Spieler bestellt am Ticker). category: null/leer = keine Kategorie zugewiesen. */
    private record PriceEntry(long sellPrice, long buyPrice, String category) {
        static final PriceEntry DEFAULT = new PriceEntry(0, 0, null);
        boolean isDefault() {
            return sellPrice <= 0 && buyPrice <= 0 && (category == null || category.isBlank());
        }
    }

    private static final class ListState {
        String name;
        final Map<String, PriceEntry> workingPrices = new LinkedHashMap<>();
        ListState(String name) { this.name = name; }
    }

    private record TrackedWidget(AbstractWidget widget, int naturalY) {}

    private final BlockPos pos;
    private boolean enabled;
    private final List<String> availableItemIds;
    /** Reihenfolge wie vom Server geliefert (LinkedHashMap) - "default" ist immer als erstes/vorhanden garantiert. */
    private final Map<String, ListState> listStates = new LinkedHashMap<>();
    private String currentListId;
    private final List<LinkedMerchantInfo> linkedEntries;

    private Button enabledButton;
    private Button listSelectButton;
    private Button sortModeButton;
    private EditBox newListNameBox;
    private EditBox searchBox;
    private EditBox itemIdBox;
    private EditBox sellPriceBox;
    private EditBox buyPriceBox;
    private EditBox categoryBox;
    private boolean listDropdownOpen = false;
    private boolean sortDropdownOpen = false;

    private SortMode sortMode = SortMode.NAME;
    private boolean sortAscending = true;
    private double scrollAmount = 0;
    private List<String> selectableIds = List.of();

    private final List<TrackedWidget> trackedWidgets = new ArrayList<>();
    private double pageScroll = 0;
    private int maxPageScroll = 0;

    private int leftPanelX;
    private int leftPanelWidth;
    private int rightPanelX;
    private double linkedPanelScroll = 0;
    private UUID linkedActionUuid = null;
    private boolean linkedActionIsCustomNpc = false;
    private boolean linkedDeleteConfirming = false;
    private int linkedActionPopupX;
    private int linkedActionPopupY;

    // ===== Verkaufserlöse-Empfänger (siehe CobbleMerchantPayoutManager) =====
    private Button saleModeButton;
    private boolean saleModeDropdownOpen = false;
    private EditBox playerPickerBox;
    private boolean playerPickerOpen = false;
    /** null = Netzwerk-weiter Empfänger (Modus SINGLE), sonst Entity-Override (Modus VARIES). */
    private UUID playerPickerTargetEntity = null;
    private int playerPickerPopupX;
    private int playerPickerPopupY;

    // ===== Item-Listen-Filter (Nutzer-Vorgabe: Buttons rechts unter Verkaufserlöse) =====
    private Button filterResetButton;
    private Button filterSellButton;
    private Button filterBuyButton;
    private Button filterCategoryButton;
    private boolean filterCategoryDropdownOpen = false;
    private boolean filterSell = false;
    private boolean filterBuy = false;
    /** null = kein Kategorie-Filter, "" = "Keine Kategorie" (nur unkategorisierte Items), sonst konkreter Kategoriename. */
    private String filterCategory = null;

    public StockTickerPriceScreen(BlockPos pos, boolean enabled, String currentListId, List<PriceListPayload> lists, List<String> availableItemIds) {
        super(Component.translatable("cobblecompanion.gui.stockticker.title"));
        this.pos = pos;
        this.enabled = enabled;
        this.availableItemIds = availableItemIds;
        this.linkedEntries = new ArrayList<>(ClientLinkedMerchantsHelper.getEntries());

        for (PriceListPayload payload : lists) {
            ListState state = new ListState(payload.name());
            for (String entry : payload.entries()) {
                int eq = entry.indexOf('=');
                if (eq <= 0) continue;
                String itemId = entry.substring(0, eq);
                String[] rest = entry.substring(eq + 1).split("=", -1);
                if (rest.length == 0) continue;
                try {
                    long sellPrice = Long.parseLong(rest[0].trim());
                    long buyPrice = sellPrice;
                    if (rest.length >= 2) {
                        try {
                            buyPrice = Long.parseLong(rest[1].trim());
                        } catch (NumberFormatException e) {
                            buyPrice = sellPrice;
                        }
                    }
                    // rest[2]/rest[3] (alte An/Aus-Flags) werden nicht mehr ausgewertet - Nutzer-
                    // Vorgabe: entfernt, ein Preis von 0 hat bereits dieselbe Wirkung.
                    String category = rest.length >= 5 ? String.join("=", Arrays.copyOfRange(rest, 4, rest.length)).trim() : "";
                    state.workingPrices.put(itemId, new PriceEntry(sellPrice, buyPrice, category.isEmpty() ? null : category));
                } catch (NumberFormatException ignored) {}
            }
            listStates.put(payload.id(), state);
        }
        if (listStates.isEmpty()) listStates.put("default", new ListState("Standard"));
        this.currentListId = listStates.containsKey(currentListId) ? currentListId : listStates.keySet().iterator().next();

        rebuildSelectableIds();
    }

    private ListState currentState() {
        return listStates.computeIfAbsent(currentListId, id -> new ListState("Standard"));
    }

    @Override
    protected void initScaled() {
        trackedWidgets.clear();
        pageScroll = 0;

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = TOP_Y;

        leftPanelX = LEFT_PANEL_MARGIN;
        leftPanelWidth = panelX - LEFT_PANEL_MARGIN * 2;
        rightPanelX = Math.min(panelX + PANEL_WIDTH + LEFT_PANEL_MARGIN, this.width - RIGHT_SALE_BUTTON_W - LEFT_PANEL_MARGIN);

        int listRowY = panelY + TOGGLE_BTN_H + LIST_ROW_GAP_Y;
        int createRowY = listRowY + LIST_ROW_H + LIST_ROW_TO_CREATE_GAP_Y;
        int importY = createRowY + LIST_ROW_H + LIST_ROW_TO_CREATE_GAP_Y;
        int exportY = importY + LIST_ROW_H + LIST_ROW_TO_CREATE_GAP_Y;
        // Nutzer-Vorgabe: Unterkante von Sortieren:/Aufsteigend auf gleicher Höhe wie Unterkante
        // von Exportieren - der Rest der Kette (Suche, Liste, ...) bleibt relativ zu sortY
        // unverändert und rutscht dadurch automatisch im gleichen Maß mit nach oben.
        int sortY = exportY + LIST_ROW_H - SORT_BTN_H;
        int searchY = sortY + SORT_BTN_H + SORT_TO_SEARCH_GAP_Y;
        int listTopY = searchY + SEARCH_BOX_H + SEARCH_TO_LIST_GAP_Y;
        int listBottomY = listTopY + LIST_HEIGHT;

        int entryY = listBottomY + LIST_TO_ENTRY_GAP_Y;
        int categoryY = entryY + ENTRY_ROW_H + ENTRY_TO_CATEGORY_GAP_Y;
        int invTop = categoryY + ENTRY_ROW_H + CATEGORY_TO_INV_GAP_Y;
        int hotbarY = invTop + INV_MAIN_ROWS * INV_SLOT_SIZE + INV_ROW_GAP_Y;
        int actionY = hotbarY + INV_SLOT_SIZE + ACTION_GAP_Y;

        enabledButton = addTracked(Button.builder(enabledLabel(), b -> {
                enabled = !enabled;
                b.setMessage(enabledLabel());
            })
            .bounds((this.width - TOGGLE_BTN_W) / 2, panelY, TOGGLE_BTN_W, TOGGLE_BTN_H)
            .build(), panelY);

        // Nutzer-Vorgabe: in den freien Bereich rechts neben dem Panel verschoben (nicht im Panel
        // selbst) - bleibt wie das linke Panel unabhängig vom Seiten-Scroll fix stehen.
        saleModeButton = Button.builder(saleModeLabel(), b -> saleModeDropdownOpen = !saleModeDropdownOpen)
            .bounds(rightPanelX, TOP_Y, RIGHT_SALE_BUTTON_W, RECIPIENT_ROW_H)
            .build();
        addRenderableWidget(saleModeButton);

        // Nutzer-Vorgabe: Filter-Buttons direkt unter Verkaufserlöse, gleiche Breite/Spalte.
        int filterResetY = TOP_Y + RECIPIENT_ROW_H + FILTER_ROW_GAP_Y;
        int filterSellY = filterResetY + RECIPIENT_ROW_H + FILTER_ROW_GAP_Y;
        int filterBuyY = filterSellY + RECIPIENT_ROW_H + FILTER_ROW_GAP_Y;
        int filterCategoryY = filterBuyY + RECIPIENT_ROW_H + FILTER_ROW_GAP_Y;

        filterResetButton = Button.builder(Component.translatable("cobblecompanion.gui.stockticker.filter_reset"), b -> resetFilters())
            .bounds(rightPanelX, filterResetY, RIGHT_SALE_BUTTON_W, RECIPIENT_ROW_H)
            .build();
        addRenderableWidget(filterResetButton);

        filterSellButton = Button.builder(Component.translatable("cobblecompanion.gui.stockticker.filter_sell"), b -> {
                filterSell = !filterSell;
                rebuildSelectableIdsAndResetScroll();
            })
            .bounds(rightPanelX, filterSellY, RIGHT_SALE_BUTTON_W, RECIPIENT_ROW_H)
            .build();
        addRenderableWidget(filterSellButton);

        filterBuyButton = Button.builder(Component.translatable("cobblecompanion.gui.stockticker.filter_buy"), b -> {
                filterBuy = !filterBuy;
                rebuildSelectableIdsAndResetScroll();
            })
            .bounds(rightPanelX, filterBuyY, RIGHT_SALE_BUTTON_W, RECIPIENT_ROW_H)
            .build();
        addRenderableWidget(filterBuyButton);

        filterCategoryButton = Button.builder(Component.translatable("cobblecompanion.gui.stockticker.filter_category"), b -> filterCategoryDropdownOpen = !filterCategoryDropdownOpen)
            .bounds(rightPanelX, filterCategoryY, RIGHT_SALE_BUTTON_W, RECIPIENT_ROW_H)
            .build();
        addRenderableWidget(filterCategoryButton);

        playerPickerBox = new EditBox(this.font, 0, 0, PLAYER_PICKER_BOX_W, PLAYER_PICKER_BOX_H, Component.empty());
        playerPickerBox.setMaxLength(32);
        playerPickerBox.visible = false;
        addRenderableWidget(playerPickerBox);

        listSelectButton = addTracked(Button.builder(listSelectLabel(), b -> listDropdownOpen = !listDropdownOpen)
            .bounds(panelX, listRowY, LIST_SELECT_BTN_W, LIST_ROW_H)
            .build(), listRowY);
        addTracked(Button.builder(Component.translatable("cobblecompanion.gui.stockticker.list_delete"), b -> deleteCurrentList())
            .bounds(panelX + PANEL_WIDTH - LIST_DELETE_BTN_W, listRowY, LIST_DELETE_BTN_W, LIST_ROW_H)
            .build(), listRowY);

        newListNameBox = new EditBox(this.font, panelX, createRowY, LIST_NAME_BOX_W, LIST_ROW_H,
            Component.translatable("cobblecompanion.gui.stockticker.list_name_hint"));
        newListNameBox.setHint(Component.translatable("cobblecompanion.gui.stockticker.list_name_hint"));
        newListNameBox.setMaxLength(64);
        addTracked(newListNameBox, createRowY);
        addTracked(Button.builder(Component.translatable("cobblecompanion.gui.stockticker.list_create"), b -> createNewList())
            .bounds(panelX + PANEL_WIDTH - LIST_CREATE_BTN_W, createRowY, LIST_CREATE_BTN_W, LIST_ROW_H)
            .build(), createRowY);

        // Nutzer-Vorgabe: Importieren direkt unter Liste anlegen, Exportieren darunter (gestapelt,
        // gleiche Breite wie Liste löschen/Liste anlegen statt einer ganzen Zeile).
        addTracked(Button.builder(Component.translatable("cobblecompanion.gui.stockticker.list_import"), b -> importList())
            .bounds(panelX + PANEL_WIDTH - EXPORT_IMPORT_BTN_W, importY, EXPORT_IMPORT_BTN_W, LIST_ROW_H)
            .build(), importY);
        addTracked(Button.builder(Component.translatable("cobblecompanion.gui.stockticker.list_export"), b -> exportList())
            .bounds(panelX + PANEL_WIDTH - EXPORT_IMPORT_BTN_W, exportY, EXPORT_IMPORT_BTN_W, LIST_ROW_H)
            .build(), exportY);

        sortModeButton = addTracked(Button.builder(sortModeLabel(), b -> sortDropdownOpen = !sortDropdownOpen)
            .bounds(panelX, sortY, SORT_BTN_W, SORT_BTN_H)
            .build(), sortY);
        addTracked(Button.builder(sortDirectionLabel(), b -> {
                sortAscending = !sortAscending;
                b.setMessage(sortDirectionLabel());
                rebuildSelectableIdsAndResetScroll();
            })
            .bounds(panelX + SORT_BTN_W + SORT_BTN_GAP_X, sortY, SORT_DIR_BTN_W, SORT_BTN_H)
            .build(), sortY);

        searchBox = new EditBox(this.font, panelX, searchY, PANEL_WIDTH, SEARCH_BOX_H,
            Component.translatable("cobblecompanion.gui.stockticker.search_hint"));
        searchBox.setHint(Component.translatable("cobblecompanion.gui.stockticker.search_hint"));
        searchBox.setMaxLength(256);
        searchBox.setResponder(s -> rebuildSelectableIdsAndResetScroll());
        addTracked(searchBox, searchY);

        int entryX = panelX;
        itemIdBox = new EditBox(this.font, entryX, entryY, ITEMID_BOX_W, ENTRY_ROW_H,
            Component.translatable("cobblecompanion.gui.stockticker.itemid_hint"));
        itemIdBox.setHint(Component.translatable("cobblecompanion.gui.stockticker.itemid_hint"));
        itemIdBox.setMaxLength(256);
        addTracked(itemIdBox, entryY);

        int sellPriceX = entryX + ITEMID_BOX_W + FIELD_GAP_X;
        sellPriceBox = new EditBox(this.font, sellPriceX, entryY, PRICE_BOX_W, ENTRY_ROW_H,
            Component.translatable("cobblecompanion.gui.stockticker.sell_price_hint"));
        sellPriceBox.setHint(Component.translatable("cobblecompanion.gui.stockticker.sell_price_hint"));
        sellPriceBox.setMaxLength(15);
        addTracked(sellPriceBox, entryY);

        int buyPriceX = sellPriceX + PRICE_BOX_W + FIELD_GAP_X;
        buyPriceBox = new EditBox(this.font, buyPriceX, entryY, PRICE_BOX_W, ENTRY_ROW_H,
            Component.translatable("cobblecompanion.gui.stockticker.buy_price_hint"));
        buyPriceBox.setHint(Component.translatable("cobblecompanion.gui.stockticker.buy_price_hint"));
        buyPriceBox.setMaxLength(15);
        addTracked(buyPriceBox, entryY);

        int applyX = buyPriceX + PRICE_BOX_W + FIELD_GAP_X;
        addTracked(Button.builder(Component.translatable("cobblecompanion.gui.stockticker.apply"), b -> applyManualEntry())
            .bounds(applyX, entryY, APPLY_BTN_W, ENTRY_ROW_H)
            .build(), entryY);

        categoryBox = new EditBox(this.font, entryX, categoryY, CATEGORY_BOX_W, ENTRY_ROW_H,
            Component.translatable("cobblecompanion.gui.stockticker.category_hint"));
        categoryBox.setHint(Component.translatable("cobblecompanion.gui.stockticker.category_hint"));
        categoryBox.setMaxLength(64);
        addTracked(categoryBox, categoryY);

        int invX = (this.width - INV_SLOT_SIZE * INV_COLUMNS) / 2;
        // Hauptinventar (Slots 9-35) als 3 Reihen, Hotbar (Slots 0-8) als 4. Reihe darunter -
        // gleiche Anordnung wie Vanilla-Inventar-GUIs.
        for (int row = 0; row < INV_MAIN_ROWS; row++) {
            int rowY = invTop + row * INV_SLOT_SIZE;
            for (int col = 0; col < INV_COLUMNS; col++) {
                int slotIndex = 9 + row * INV_COLUMNS + col;
                int x = invX + col * INV_SLOT_SIZE;
                addTracked(Button.builder(Component.empty(), b -> selectInventorySlot(slotIndex))
                    .bounds(x, rowY, INV_SLOT_SIZE, INV_SLOT_SIZE)
                    .build(), rowY);
            }
        }
        for (int i = 0; i < INV_COLUMNS; i++) {
            int slotIndex = i;
            int x = invX + i * INV_SLOT_SIZE;
            addTracked(Button.builder(Component.empty(), b -> selectInventorySlot(slotIndex))
                .bounds(x, hotbarY, INV_SLOT_SIZE, INV_SLOT_SIZE)
                .build(), hotbarY);
        }

        addTracked(Button.builder(Component.translatable("cobblecompanion.gui.stockticker.save"), b -> save())
            .bounds(panelX, actionY, ACTION_BTN_W, ACTION_BTN_H)
            .build(), actionY);
        addTracked(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(panelX + PANEL_WIDTH - ACTION_BTN_W, actionY, ACTION_BTN_W, ACTION_BTN_H)
            .build(), actionY);

        int contentBottom = actionY + ACTION_BTN_H + BOTTOM_MARGIN;
        maxPageScroll = Math.max(0, contentBottom - this.height);
        applyPageScroll();
    }

    private <T extends AbstractWidget> T addTracked(T widget, int naturalY) {
        trackedWidgets.add(new TrackedWidget(widget, naturalY));
        addRenderableWidget(widget);
        return widget;
    }

    /** Verschiebt alle gemerkten Widgets gemäß dem aktuellen Seiten-Scroll - siehe Klassenkommentar. */
    private void applyPageScroll() {
        int offset = (int) pageScroll;
        for (TrackedWidget tw : trackedWidgets) {
            tw.widget().setY(tw.naturalY() - offset);
        }
    }

    /** Wandelt eine "natürliche" (unverschobene) Y-Koordinate in die aktuelle Bildschirm-Y-Koordinate um - für alle manuell gezeichneten Elemente. */
    private int sy(int naturalY) {
        return naturalY - (int) pageScroll;
    }

    private Component enabledLabel() {
        return Component.translatable(enabled
            ? "cobblecompanion.gui.stockticker.enabled_on"
            : "cobblecompanion.gui.stockticker.enabled_off");
    }

    private Component listSelectLabel() {
        return Component.translatable("cobblecompanion.gui.stockticker.list_select", currentState().name);
    }

    private Component saleModeLabel() {
        String mode = ClientSaleRecipientHelper.getMode();
        return switch (mode) {
            case CobbleMerchantPayoutManager.MODE_SINGLE -> {
                String name = ClientSaleRecipientHelper.getRecipientName();
                yield Component.translatable("cobblecompanion.gui.stockticker.recipient_single",
                    name.isEmpty() ? Component.translatable("cobblecompanion.gui.stockticker.recipient_none").getString() : name);
            }
            case CobbleMerchantPayoutManager.MODE_VARIES -> Component.translatable("cobblecompanion.gui.stockticker.recipient_varies_label");
            default -> Component.translatable("cobblecompanion.gui.stockticker.recipient_none_label");
        };
    }

    private static final UUID NO_RECIPIENT_PLACEHOLDER = new UUID(0, 0);

    private void sendSaleModeUpdate(String mode, UUID recipientUuid) {
        // UUIDUtil.STREAM_CODEC kann kein null encodieren - bei fehlendem Empfänger einen
        // Platzhalter mitschicken, das hasRecipient-Flag entscheidet serverseitig, ob er benutzt wird.
        sendToServer(new SaleRecipientNetworkUpdatePacket(pos, mode, recipientUuid != null,
            recipientUuid != null ? recipientUuid : NO_RECIPIENT_PLACEHOLDER));
    }

    private void openPlayerPicker(UUID targetEntity, int x, int y) {
        playerPickerOpen = true;
        playerPickerTargetEntity = targetEntity;
        playerPickerPopupX = x;
        playerPickerPopupY = y;
        playerPickerBox.setX(x);
        playerPickerBox.setY(y);
        playerPickerBox.setValue("");
        playerPickerBox.visible = true;
        playerPickerBox.setFocused(true);
        this.setFocused(playerPickerBox);
    }

    private void closePlayerPicker() {
        playerPickerOpen = false;
        playerPickerTargetEntity = null;
        playerPickerBox.visible = false;
        playerPickerBox.setFocused(false);
    }

    /** Bis zu MAX_PLAYER_SUGGESTIONS bekannte Spieler (siehe ClientKnownPlayersHelper), gefiltert nach dem Tipptext. Bei Entity-Overrides zusätzlich ein "kein Empfänger"-Eintrag ganz oben. */
    private List<String> playerPickerSuggestions() {
        String typed = playerPickerBox.getValue().trim().toLowerCase();
        List<String> result = new ArrayList<>();
        if (playerPickerTargetEntity != null) {
            result.add(Component.translatable("cobblecompanion.gui.stockticker.recipient_none").getString());
        }
        for (KnownPlayersSyncPacket.Entry entry : ClientKnownPlayersHelper.getPlayers()) {
            if (typed.isEmpty() || entry.name().toLowerCase().contains(typed)) {
                result.add(entry.name());
                if (result.size() >= MAX_PLAYER_SUGGESTIONS) break;
            }
        }
        return result;
    }

    private void choosePlayerPickerSuggestion(String name) {
        boolean isClear = playerPickerTargetEntity != null
            && name.equals(Component.translatable("cobblecompanion.gui.stockticker.recipient_none").getString());
        UUID uuid = null;
        if (!isClear) {
            for (KnownPlayersSyncPacket.Entry entry : ClientKnownPlayersHelper.getPlayers()) {
                if (entry.name().equals(name)) {
                    uuid = entry.uuid();
                    break;
                }
            }
            if (uuid == null) return;
        }

        if (playerPickerTargetEntity == null) {
            sendSaleModeUpdate(CobbleMerchantPayoutManager.MODE_SINGLE, uuid);
        } else {
            sendToServer(new SaleRecipientEntityUpdatePacket(pos, playerPickerTargetEntity, uuid != null,
                uuid != null ? uuid : NO_RECIPIENT_PLACEHOLDER));
        }
        closePlayerPicker();
    }

    private Component sortModeLabel() {
        return sortModeLabel(sortMode);
    }

    private static Component sortModeLabel(SortMode mode) {
        return switch (mode) {
            case NAME -> Component.translatable("cobblecompanion.gui.stockticker.sort_name");
            case ANKAUF -> Component.translatable("cobblecompanion.gui.stockticker.sort_ankauf");
            case VERKAUF -> Component.translatable("cobblecompanion.gui.stockticker.sort_verkauf");
        };
    }

    private Component sortDirectionLabel() {
        return Component.translatable(sortAscending
            ? "cobblecompanion.gui.stockticker.sort_asc"
            : "cobblecompanion.gui.stockticker.sort_desc");
    }

    private void switchToList(String listId) {
        if (!listStates.containsKey(listId)) return;
        currentListId = listId;
        listDropdownOpen = false;
        listSelectButton.setMessage(listSelectLabel());
        rebuildSelectableIdsAndResetScroll();
    }

    private void switchSortMode(SortMode mode) {
        sortMode = mode;
        sortDropdownOpen = false;
        sortModeButton.setMessage(sortModeLabel());
        rebuildSelectableIdsAndResetScroll();
    }

    private void createNewList() {
        String name = newListNameBox.getValue().trim();
        if (name.isEmpty()) return;
        String id = UUID.randomUUID().toString();
        listStates.put(id, new ListState(name));
        newListNameBox.setValue("");
        switchToList(id);
    }

    /** Client-seitiger Spiegel der serverseitigen Schutzregeln (deleteList) - "default" bzw. die letzte verbleibende Liste kann nicht gelöscht werden. */
    private void deleteCurrentList() {
        if ("default".equals(currentListId) || listStates.size() <= 1) return;
        listStates.remove(currentListId);
        String fallback = listStates.containsKey("default") ? "default" : listStates.keySet().iterator().next();
        switchToList(fallback);
    }

    /** Nutzer-Vorgabe: Preisliste in die System-Zwischenablage exportieren, um sie in ein anderes Netzwerk (oder einen anderen Server/Singleplayer) zu kopieren. */
    private void exportList() {
        StringBuilder sb = new StringBuilder();
        sb.append(EXPORT_MARKER).append('\n');
        sb.append(currentState().name).append('\n');
        for (Map.Entry<String, PriceEntry> entry : currentState().workingPrices.entrySet()) {
            PriceEntry v = entry.getValue();
            sb.append(entry.getKey()).append('=').append(v.sellPrice()).append('=').append(v.buyPrice())
                .append("=1=1=").append(v.category() != null ? v.category() : "").append('\n');
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
    }

    /** Fügt die per exportList() in die Zwischenablage kopierte Liste in die AKTUELL geöffnete Liste ein (überschreibt gleiche Item-IDs, lässt den Rest unangetastet). Ungültiger/fremder Zwischenablage-Inhalt wird stillschweigend ignoriert. */
    private void importList() {
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard == null) return;
        String[] lines = clipboard.split("\n", -1);
        if (lines.length < 1 || !EXPORT_MARKER.equals(lines[0].trim())) return;

        Map<String, PriceEntry> workingPrices = currentState().workingPrices;
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String itemId = line.substring(0, eq);
            String[] rest = line.substring(eq + 1).split("=", -1);
            if (rest.length == 0) continue;
            try {
                long sellPrice = Long.parseLong(rest[0].trim());
                long buyPrice = sellPrice;
                if (rest.length >= 2) {
                    try {
                        buyPrice = Long.parseLong(rest[1].trim());
                    } catch (NumberFormatException e) {
                        buyPrice = sellPrice;
                    }
                }
                String category = rest.length >= 5 ? String.join("=", Arrays.copyOfRange(rest, 4, rest.length)).trim() : "";
                workingPrices.put(itemId, new PriceEntry(sellPrice, buyPrice, category.isEmpty() ? null : category));
            } catch (NumberFormatException ignored) {}
        }
        rebuildSelectableIds();
        sendUpdate();
    }

    private String displayNameOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
        return item != null ? Component.translatable(item.getDescriptionId()).getString() : id;
    }

    /** Verfügbare Items + bereits bepreiste/kategorisierte Items (auch falls gerade nicht verfügbar), gefiltert nach Suchtext, dedupliziert + sortiert. */
    private void rebuildSelectableIds() {
        Set<String> ids = new LinkedHashSet<>(availableItemIds);
        ids.addAll(currentState().workingPrices.keySet());

        String search = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        List<String> list = new ArrayList<>();
        for (String id : ids) {
            if (!(search.isEmpty() || id.toLowerCase().contains(search) || displayNameOf(id).toLowerCase().contains(search))) continue;

            PriceEntry entry = currentState().workingPrices.get(id);
            if (filterSell && (entry == null || entry.buyPrice() <= 0)) continue;
            if (filterBuy && (entry == null || entry.sellPrice() <= 0)) continue;
            if (filterCategory != null) {
                String category = entry != null ? entry.category() : null;
                boolean hasCategory = category != null && !category.isBlank();
                if (filterCategory.isEmpty()) {
                    if (hasCategory) continue;
                } else if (!filterCategory.equals(category)) {
                    continue;
                }
            }
            list.add(id);
        }

        Comparator<String> comparator = switch (sortMode) {
            case NAME -> Comparator.comparing(this::displayNameOf, String.CASE_INSENSITIVE_ORDER);
            case ANKAUF -> Comparator.comparingLong(id -> currentState().workingPrices.getOrDefault(id, PriceEntry.DEFAULT).sellPrice());
            case VERKAUF -> Comparator.comparingLong(id -> currentState().workingPrices.getOrDefault(id, PriceEntry.DEFAULT).buyPrice());
        };
        if (!sortAscending) comparator = comparator.reversed();
        list.sort(comparator);
        this.selectableIds = list;
        // Nutzer-Vorgabe: "Übernehmen"/Zurücksetzen-X auf einer einzelnen Zeile soll die Liste NICHT
        // an den Anfang zurückspringen lassen (bearbeitet man ein Item mitten in einer langen Liste,
        // ist das sehr störend) - der Scroll wird deshalb hier bewusst NICHT zurückgesetzt, sondern
        // nur an allen Stellen, wo sich der Listeninhalt grundlegend ändert (Suche, Sortierung,
        // Filter, Listenwechsel - siehe die jeweiligen Aufrufer).
        this.scrollAmount = Math.max(0, Math.min(this.scrollAmount, Math.max(0, selectableIds.size() * ROW_H - LIST_HEIGHT)));
    }

    /** Wie rebuildSelectableIds(), setzt aber zusätzlich den Scroll zurück - für Stellen, an denen sich der Listeninhalt grundlegend ändert (Suche, Sortierung, Filter, Listenwechsel). */
    private void rebuildSelectableIdsAndResetScroll() {
        scrollAmount = 0;
        rebuildSelectableIds();
    }

    private void resetFilters() {
        filterSell = false;
        filterBuy = false;
        filterCategory = null;
        filterCategoryDropdownOpen = false;
        rebuildSelectableIdsAndResetScroll();
    }

    /** Alle in der aktuellen Liste vorkommenden Kategorien (ohne Duplikate, sortiert) - für das Filter-Kategorien-Dropdown. */
    private List<String> knownCategoriesForFilter() {
        Set<String> categories = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (PriceEntry entry : currentState().workingPrices.values()) {
            if (entry.category() != null && !entry.category().isBlank()) categories.add(entry.category());
        }
        return new ArrayList<>(categories);
    }

    private Component filterCategoryStateLabel() {
        if (filterCategory == null) return Component.translatable("cobblecompanion.gui.stockticker.filter_off");
        if (filterCategory.isEmpty()) return Component.translatable("cobblecompanion.gui.stockticker.filter_category_none");
        return Component.literal(filterCategory);
    }

    private void selectInventorySlot(int slotIndex) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getInventory().getItem(slotIndex);
        if (stack.isEmpty()) return;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        loadIntoForm(id);
    }

    private void applyManualEntry() {
        String id = itemIdBox.getValue().trim();
        if (id.isEmpty()) return;
        if (!id.contains(":")) id = "minecraft:" + id;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return;

        long sellPrice;
        if (sellPriceBox.getValue().trim().isEmpty()) {
            sellPrice = 0;
        } else {
            java.math.BigInteger parsed = CobbleDollarsScale.parseToRaw(sellPriceBox.getValue().trim());
            if (parsed == null) return;
            sellPrice = parsed.longValueExact();
        }
        long buyPrice;
        if (buyPriceBox.getValue().trim().isEmpty()) {
            buyPrice = 0;
        } else {
            java.math.BigInteger parsed = CobbleDollarsScale.parseToRaw(buyPriceBox.getValue().trim());
            if (parsed == null) return;
            buyPrice = parsed.longValueExact();
        }
        String category = categoryBox.getValue().trim();
        PriceEntry entry = new PriceEntry(sellPrice, buyPrice, category.isEmpty() ? null : category);
        Map<String, PriceEntry> workingPrices = currentState().workingPrices;
        if (entry.isDefault()) workingPrices.remove(rl.toString());
        else workingPrices.put(rl.toString(), entry);
        itemIdBox.setValue("");
        sellPriceBox.setValue("");
        buyPriceBox.setValue("");
        categoryBox.setValue("");
        rebuildSelectableIds();
        // Nutzer-Vorgabe: "Übernehmen" speichert sofort mit (nicht erst beim expliziten
        // "Speichern") - vermeidet Datenverlust, wenn der Screen danach versehentlich per ESC
        // geschlossen wird, statt den Speichern-Button zu klicken.
        sendUpdate();
    }

    private void selectRow(String id) {
        loadIntoForm(id);
    }

    /** Nutzer-Vorgabe: rotes X am Zeilenende - Preise+Kategorie dieses Items komplett zurücksetzen. */
    private void resetRow(String id) {
        currentState().workingPrices.remove(id);
        if (id.equals(itemIdBox.getValue().trim())) loadIntoForm(id);
        rebuildSelectableIds();
        sendUpdate();
    }

    private void loadIntoForm(String id) {
        itemIdBox.setValue(id);
        PriceEntry existing = currentState().workingPrices.get(id);
        sellPriceBox.setValue(existing != null && existing.sellPrice() > 0 ? CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(existing.sellPrice())) : "");
        buyPriceBox.setValue(existing != null && existing.buyPrice() > 0 ? CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(existing.buyPrice())) : "");
        categoryBox.setValue(existing != null && existing.category() != null ? existing.category() : "");
    }

    /** Bis zu MAX_CATEGORY_SUGGESTIONS bekannte Kategorien der aktuellen Liste, gefiltert nach dem Tipptext in categoryBox. */
    private List<String> categorySuggestions() {
        String typed = categoryBox.getValue().trim().toLowerCase();
        Set<String> known = new LinkedHashSet<>();
        for (PriceEntry entry : currentState().workingPrices.values()) {
            if (entry.category() != null && !entry.category().isBlank()) known.add(entry.category());
        }
        List<String> result = new ArrayList<>();
        for (String category : known) {
            if (typed.isEmpty() || category.toLowerCase().contains(typed)) {
                result.add(category);
                if (result.size() >= MAX_CATEGORY_SUGGESTIONS) break;
            }
        }
        return result;
    }

    /** Bis zu MAX_ITEMID_SUGGESTIONS Item-IDs aus der kompletten Registry, gefiltert nach ID ODER lokalisiertem Namen (aktuelle Client-Sprache). */
    private List<String> itemIdSuggestions() {
        String typed = itemIdBox.getValue().trim().toLowerCase();
        if (typed.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            String id = rl.toString();
            boolean matches = id.toLowerCase().contains(typed) || displayNameOf(id).toLowerCase().contains(typed);
            if (matches) {
                result.add(id);
                if (result.size() >= MAX_ITEMID_SUGGESTIONS) break;
            }
        }
        return result;
    }

    private void sendUpdate() {
        List<PriceListPayload> payloads = new ArrayList<>();
        for (Map.Entry<String, ListState> listEntry : listStates.entrySet()) {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, PriceEntry> entry : listEntry.getValue().workingPrices.entrySet()) {
                PriceEntry v = entry.getValue();
                // Enabled-Flags werden nicht mehr genutzt (siehe Klassenkommentar), immer "1".
                lines.add(entry.getKey() + "=" + v.sellPrice() + "=" + v.buyPrice() + "=1=1"
                    + "=" + (v.category() != null ? v.category() : ""));
            }
            payloads.add(new PriceListPayload(listEntry.getKey(), listEntry.getValue().name, lines));
        }
        sendToServer(new CreateStockTickerPricesUpdatePacket(pos, enabled, currentListId, payloads));
    }

    private void save() {
        sendUpdate();
        onClose();
    }

    // ===== Verknüpfte-NPCs/Merchants-Panel (links) =====

    private int linkedPanelTop() {
        return TOP_Y;
    }

    private int linkedPanelBottom() {
        return this.height - BOTTOM_MARGIN;
    }

    private boolean showLeftPanel() {
        return leftPanelWidth >= LEFT_PANEL_MIN_WIDTH;
    }

    private void closeLinkedActionPopup() {
        linkedActionUuid = null;
        linkedDeleteConfirming = false;
    }

    private LinkedMerchantInfo findLinkedEntry(UUID uuid) {
        for (LinkedMerchantInfo info : linkedEntries) {
            if (info.uuid().equals(uuid)) return info;
        }
        return null;
    }

    private void sendLinkedAction(int action) {
        if (linkedActionUuid == null) return;
        sendToServer(new LinkedMerchantActionPacket(linkedActionUuid, linkedActionIsCustomNpc, action));

        // Lokal sofort spiegeln, statt auf einen Server-Roundtrip zu warten.
        LinkedMerchantInfo info = findLinkedEntry(linkedActionUuid);
        if (info != null) {
            if (action == LinkedMerchantActionPacket.ACTION_DISCONNECT_STORAGE) {
                linkedEntries.set(linkedEntries.indexOf(info), new LinkedMerchantInfo(info.uuid(), info.isCustomNpc(), info.name(),
                    info.dimension(), info.x(), info.y(), info.z(), false, "", 0, 0, 0));
            } else {
                // Ticker getrennt ODER gelöscht -> gehört nicht mehr zu diesem Netzwerk/existiert nicht mehr.
                linkedEntries.remove(info);
            }
        }
        closeLinkedActionPopup();
    }

    /**
     * Öffnet den individuellen Angebots-Editor für den gerade im Popup ausgewählten Merchant
     * (siehe MerchantOfferEditScreen-Klassenkommentar) - Netzwerk-Angebot + Kategorien werden
     * bewusst NICHT vom Server neu angefragt, sondern direkt aus der hier bereits geladenen
     * Preisliste des aktuellen Netzwerks abgeleitet (identisches Aufnahmekriterium wie
     * PlayerExtensionKtOpenShopMixin: aktiver Ankaufspreis > 0 ODER eine gesetzte Kategorie).
     */
    private void openMerchantOfferEditor() {
        if (linkedActionUuid == null) return;
        LinkedMerchantInfo info = findLinkedEntry(linkedActionUuid);
        String name = info != null ? info.name() : linkedActionUuid.toString();

        List<String> networkOfferIds = new ArrayList<>();
        Map<String, String> categories = new LinkedHashMap<>();
        for (Map.Entry<String, PriceEntry> entry : currentState().workingPrices.entrySet()) {
            PriceEntry v = entry.getValue();
            boolean hasCategory = v.category() != null && !v.category().isBlank();
            if (v.buyPrice() <= 0 && !hasCategory) continue;
            networkOfferIds.add(entry.getKey());
            if (hasCategory) categories.put(entry.getKey(), v.category());
        }

        ClientMerchantOfferHelper.requestEdit(pos, linkedActionUuid, name, networkOfferIds, categories);
        sendToServer(new MerchantOfferRequestPacket(linkedActionUuid));
        closeLinkedActionPopup();
    }

    private void renderLeftPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!showLeftPanel()) return;

        int top = linkedPanelTop();
        int bottom = linkedPanelBottom();
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.stockticker.linked_title"), leftPanelX, top - 12, 0xFFFFFF);

        graphics.fill(leftPanelX, top, leftPanelX + leftPanelWidth, bottom, 0x80000000);
        graphics.enableScissor(toRealX(leftPanelX), toRealY(top), toRealX(leftPanelX + leftPanelWidth), toRealY(bottom));
        int maxScroll = Math.max(0, linkedEntries.size() * LEFT_ROW_H - (bottom - top));
        linkedPanelScroll = Math.max(0, Math.min(maxScroll, linkedPanelScroll));
        int y = top - (int) Math.round(linkedPanelScroll);
        for (LinkedMerchantInfo info : linkedEntries) {
            if (y + LEFT_ROW_H >= top && y <= bottom) {
                renderLinkedRow(graphics, info, leftPanelX, y, mouseX, mouseY);
            }
            y += LEFT_ROW_H;
        }
        graphics.disableScissor();

        if (linkedActionUuid != null) {
            List<String> options = linkedDeleteConfirming
                ? List.of(Component.translatable("cobblecompanion.gui.stockticker.linked_delete_confirm_yes").getString(),
                          Component.translatable("cobblecompanion.gui.stockticker.linked_delete_confirm_no").getString())
                : List.of(Component.translatable("cobblecompanion.gui.stockticker.linked_disconnect_storage").getString(),
                          Component.translatable("cobblecompanion.gui.stockticker.linked_disconnect_ticker").getString(),
                          Component.translatable("cobblecompanion.gui.stockticker.linked_edit_offer").getString(),
                          Component.translatable("cobblecompanion.gui.stockticker.linked_delete").getString());
            renderSuggestions(graphics, mouseX, mouseY, linkedActionPopupX, linkedActionPopupY, ACTION_POPUP_W, options);
        }
    }

    private void renderLinkedRow(GuiGraphics graphics, LinkedMerchantInfo info, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + leftPanelWidth && mouseY >= y && mouseY < y + LEFT_ROW_H;
        if (hovered) {
            graphics.fill(x, y, x + leftPanelWidth, y + LEFT_ROW_H, 0x40FFFFFF);
        }

        int previewY = y + (LEFT_ROW_H - LEFT_PREVIEW_SIZE) / 2;
        renderLinkedPreview(graphics, info, x + 2, previewY, LEFT_PREVIEW_SIZE, mouseX, mouseY);

        int textX = x + LEFT_PREVIEW_SIZE + 2 + LEFT_TEXT_GAP_X;
        int maxTextWidth = leftPanelWidth - (LEFT_PREVIEW_SIZE + 2 + LEFT_TEXT_GAP_X) - 4;
        String name = this.font.plainSubstrByWidth(info.name(), Math.max(10, maxTextWidth));
        graphics.drawString(this.font, name, textX, y + 3, 0xFFFFFF, false);
        String posLine = info.x() + "," + info.y() + "," + info.z();
        graphics.drawString(this.font, posLine, textX, y + 14, 0xA0A0A0, false);
        String storageLine = info.hasStorage()
            ? Component.translatable("cobblecompanion.gui.stockticker.linked_storage_at", info.storageX(), info.storageY(), info.storageZ()).getString()
            : Component.translatable("cobblecompanion.gui.stockticker.linked_no_storage").getString();
        storageLine = this.font.plainSubstrByWidth(storageLine, Math.max(10, maxTextWidth));
        graphics.drawString(this.font, storageLine, textX, y + 25, 0xA0A0A0, false);

        if (CobbleMerchantPayoutManager.MODE_VARIES.equals(ClientSaleRecipientHelper.getMode())) {
            String recipientName = "";
            for (SaleRecipientSyncPacket.EntityRecipientEntry entry : ClientSaleRecipientHelper.getEntityRecipients()) {
                if (entry.entityUuid().equals(info.uuid())) {
                    recipientName = entry.recipientName();
                    break;
                }
            }
            String recipientLine = Component.translatable("cobblecompanion.gui.stockticker.linked_recipient",
                recipientName.isEmpty() ? Component.translatable("cobblecompanion.gui.stockticker.recipient_none").getString() : recipientName).getString();
            recipientLine = this.font.plainSubstrByWidth(recipientLine, Math.max(10, maxTextWidth));
            graphics.drawString(this.font, recipientLine, textX, y + LEFT_RECIPIENT_LINE_Y, 0x55AAFF, false);
        }
    }

    /** Rendert die Entity live (falls clientseitig geladen), sonst einen schlichten Platzhalter statt eines Fehlers. ClientLevel bietet keinen öffentlichen UUID-Getter, daher Suche über entitiesForRendering(). */
    private void renderLinkedPreview(GuiGraphics graphics, LinkedMerchantInfo info, int x, int y, int size, int mouseX, int mouseY) {
        Entity entity = null;
        if (Minecraft.getInstance().level != null) {
            for (Entity candidate : Minecraft.getInstance().level.entitiesForRendering()) {
                if (candidate.getUUID().equals(info.uuid())) {
                    entity = candidate;
                    break;
                }
            }
        }
        if (entity instanceof LivingEntity living) {
            // Nutzer-Vorgabe (FixedScaleScreen): InventoryScreen.renderEntityInInventoryFollowsAngle
            // ruft intern enableScissor() mit den ihr übergebenen Koordinaten auf - enableScissor
            // ignoriert den PoseStack, deshalb müssen sowohl die Koordinaten (toRealX/toRealY) ALS
            // AUCH der PoseStack (renderInRealSpace) auf die echte Bildschirmfläche umgerechnet
            // werden, sonst ist die Entity bei GUI-Größe != 2 falsch/gar nicht sichtbar.
            renderInRealSpace(graphics, () -> InventoryScreen.renderEntityInInventoryFollowsAngle(graphics,
                toRealX(x), toRealY(y), toRealX(x + size), toRealY(y + size), toRealX(size) / 2, 0.0F, 0.0F, 0.0F, living));
        } else {
            graphics.fill(x, y, x + size, y + size, 0x40FFFFFF);
        }
    }

    private boolean handleLeftPanelClick(double mouseX, double mouseY, int button) {
        if (linkedActionUuid != null) {
            int count = linkedDeleteConfirming ? 2 : 4;
            if (mouseX >= linkedActionPopupX && mouseX < linkedActionPopupX + ACTION_POPUP_W
                    && mouseY >= linkedActionPopupY && mouseY < linkedActionPopupY + count * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - linkedActionPopupY) / SUGGEST_ROW_H);
                if (linkedDeleteConfirming) {
                    if (index == 0) sendLinkedAction(LinkedMerchantActionPacket.ACTION_DELETE);
                    else closeLinkedActionPopup();
                } else {
                    if (index == 0) sendLinkedAction(LinkedMerchantActionPacket.ACTION_DISCONNECT_STORAGE);
                    else if (index == 1) sendLinkedAction(LinkedMerchantActionPacket.ACTION_DISCONNECT_TICKER);
                    else if (index == 2) openMerchantOfferEditor();
                    else if (index == 3) linkedDeleteConfirming = true;
                }
                return true;
            }
            closeLinkedActionPopup();
            return true;
        }

        if (!showLeftPanel()) return false;
        int top = linkedPanelTop();
        int bottom = linkedPanelBottom();
        if (mouseX >= leftPanelX && mouseX < leftPanelX + leftPanelWidth && mouseY >= top && mouseY < bottom) {
            int index = (int) ((mouseY - top + linkedPanelScroll) / LEFT_ROW_H);
            if (index >= 0 && index < linkedEntries.size()) {
                LinkedMerchantInfo info = linkedEntries.get(index);

                // Nutzer-Vorgabe: Rechtsklick weist bei Modus VARIES den Verkaufserlös-Empfänger
                // dieser Entity zu, Linksklick öffnet wie bisher das Trennen/Löschen-Popup - beide
                // Aktionen bleiben so unabhängig vom Modus erreichbar.
                if (button == 1 && CobbleMerchantPayoutManager.MODE_VARIES.equals(ClientSaleRecipientHelper.getMode())) {
                    int rowTop = (int) (top - linkedPanelScroll + index * LEFT_ROW_H);
                    openPlayerPicker(info.uuid(), leftPanelX, rowTop + LEFT_ROW_H);
                    return true;
                }
                if (button != 0) return true;

                linkedActionUuid = info.uuid();
                linkedActionIsCustomNpc = info.isCustomNpc();
                linkedDeleteConfirming = false;
                linkedActionPopupX = leftPanelX;
                linkedActionPopupY = (int) Math.min(mouseY, bottom - SUGGEST_ROW_H * 4);
                return true;
            }
        }
        return false;
    }

    // ===== Rendering =====

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        // Wird server-seitig per SaleRecipientSyncPacket aktualisiert (siehe ClientSaleRecipientHelper) -
        // Label hier statt nur einmalig in init() jeden Frame neu setzen, sonst zeigt der Button
        // erst nach Schließen/Neuöffnen des Fensters den aktuellen Stand.
        saleModeButton.setMessage(saleModeLabel());
        renderWidgets(graphics, mouseX, mouseY, partialTick);

        renderLeftPanel(graphics, mouseX, mouseY);

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int listTop = sy(listTopNatural());
        int listBottom = listTop + LIST_HEIGHT;

        graphics.fill(panelX, listTop, panelX + PANEL_WIDTH, listBottom, 0x80000000);
        graphics.enableScissor(toRealX(panelX), toRealY(listTop), toRealX(panelX + PANEL_WIDTH), toRealY(listBottom));
        int maxScroll = Math.max(0, selectableIds.size() * ROW_H - LIST_HEIGHT);
        scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount));
        int y = listTop - (int) Math.round(scrollAmount);
        for (String id : selectableIds) {
            if (y + ROW_H >= listTop && y <= listBottom) {
                renderRow(graphics, panelX, y, id, mouseX, mouseY);
            }
            y += ROW_H;
        }
        graphics.disableScissor();

        int invTop = sy(categoryYNatural() + ENTRY_ROW_H + CATEGORY_TO_INV_GAP_Y);
        int invX = (this.width - INV_SLOT_SIZE * INV_COLUMNS) / 2;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (int row = 0; row < INV_MAIN_ROWS; row++) {
                int rowY = invTop + row * INV_SLOT_SIZE;
                for (int col = 0; col < INV_COLUMNS; col++) {
                    int slotIndex = 9 + row * INV_COLUMNS + col;
                    renderInventorySlotIcon(graphics, player, slotIndex, invX + col * INV_SLOT_SIZE, rowY);
                }
            }
            int hotbarY = invTop + INV_MAIN_ROWS * INV_SLOT_SIZE + INV_ROW_GAP_Y;
            for (int i = 0; i < INV_COLUMNS; i++) {
                renderInventorySlotIcon(graphics, player, i, invX + i * INV_SLOT_SIZE, hotbarY);
            }
        }

        // Autocomplete-Boxen: nur solange das jeweilige Feld fokussiert ist (sonst würden sie
        // permanent über der restlichen GUI schweben).
        if (itemIdBox.isFocused()) {
            renderSuggestions(graphics, mouseX, mouseY, itemIdBox.getX(), itemIdBox.getY() + itemIdBox.getHeight(),
                ITEMID_BOX_W, itemIdSuggestions());
        }
        if (categoryBox.isFocused()) {
            renderSuggestions(graphics, mouseX, mouseY, categoryBox.getX(), categoryBox.getY() + categoryBox.getHeight(),
                CATEGORY_BOX_W, categorySuggestions());
        }

        // Dropdowns zuletzt, damit sie über allem anderen liegen.
        if (sortDropdownOpen) {
            List<String> labels = new ArrayList<>();
            for (SortMode mode : SortMode.values()) labels.add(sortModeLabel(mode).getString());
            renderSuggestions(graphics, mouseX, mouseY, sortModeButton.getX(), sortModeButton.getY() + sortModeButton.getHeight(),
                SORT_BTN_W, labels);
        }
        if (listDropdownOpen) {
            List<String> names = new ArrayList<>();
            for (ListState state : listStates.values()) names.add(state.name);
            renderSuggestions(graphics, mouseX, mouseY, listSelectButton.getX(), listSelectButton.getY() + listSelectButton.getHeight(),
                LIST_SELECT_BTN_W, names);
        }
        // Nutzer-Vorgabe: Zustands-Text rechts neben den Filter-Buttons (Verkauf/Ankauf-Filter =
        // Ein/Aus, Kategorie-Filter = aktuell gewählte Kategorie bzw. "Aus").
        graphics.drawString(this.font, filterSell
                ? Component.translatable("cobblecompanion.gui.stockticker.filter_on").getString()
                : Component.translatable("cobblecompanion.gui.stockticker.filter_off").getString(),
            filterSellButton.getX() + filterSellButton.getWidth() + FILTER_STATE_TEXT_GAP_X, filterSellButton.getY() + 4, 0xFFFFFF, false);
        graphics.drawString(this.font, filterBuy
                ? Component.translatable("cobblecompanion.gui.stockticker.filter_on").getString()
                : Component.translatable("cobblecompanion.gui.stockticker.filter_off").getString(),
            filterBuyButton.getX() + filterBuyButton.getWidth() + FILTER_STATE_TEXT_GAP_X, filterBuyButton.getY() + 4, 0xFFFFFF, false);
        graphics.drawString(this.font, filterCategoryStateLabel(),
            filterCategoryButton.getX() + filterCategoryButton.getWidth() + FILTER_STATE_TEXT_GAP_X, filterCategoryButton.getY() + 4, 0xFFFFFF, false);

        if (saleModeDropdownOpen) {
            List<String> options = List.of(
                Component.translatable("cobblecompanion.gui.stockticker.recipient_none_label").getString(),
                Component.translatable("cobblecompanion.gui.stockticker.recipient_pick_player").getString(),
                Component.translatable("cobblecompanion.gui.stockticker.recipient_varies_label").getString());
            renderSuggestions(graphics, mouseX, mouseY, saleModeButton.getX() + saleModeButton.getWidth() + DROPDOWN_RIGHT_GAP_X, saleModeButton.getY(),
                RECIPIENT_MODE_POPUP_W, options);
        }
        if (filterCategoryDropdownOpen) {
            List<String> options = new ArrayList<>();
            options.add(Component.translatable("cobblecompanion.gui.stockticker.filter_category_none").getString());
            options.addAll(knownCategoriesForFilter());
            renderSuggestions(graphics, mouseX, mouseY, filterCategoryButton.getX() + filterCategoryButton.getWidth() + DROPDOWN_RIGHT_GAP_X, filterCategoryButton.getY(),
                RECIPIENT_MODE_POPUP_W, options);
        }
        if (playerPickerOpen) {
            renderSuggestions(graphics, mouseX, mouseY, playerPickerPopupX, playerPickerPopupY + PLAYER_PICKER_BOX_H, PLAYER_PICKER_BOX_W, playerPickerSuggestions());
        }

        // Seiten-Scrollbar: nur wenn nicht alles ins Fenster passt (Nutzer-Vorgabe: erkennt selbst ob nötig).
        if (maxPageScroll > 0) {
            int trackTop = 4;
            int trackBottom = this.height - 4;
            int trackHeight = trackBottom - trackTop;
            int thumbHeight = Math.max(20, trackHeight * trackHeight / (trackHeight + maxPageScroll));
            int thumbTop = trackTop + (int) ((trackHeight - thumbHeight) * (pageScroll / maxPageScroll));
            int barX = this.width - SCROLLBAR_W - 2;
            graphics.fill(barX, trackTop, barX + SCROLLBAR_W, trackBottom, 0x40FFFFFF);
            graphics.fill(barX, thumbTop, barX + SCROLLBAR_W, thumbTop + thumbHeight, 0xC0FFFFFF);
        }
    }

    private int listTopNatural() {
        int listRowY = TOP_Y + TOGGLE_BTN_H + LIST_ROW_GAP_Y;
        int createRowY = listRowY + LIST_ROW_H + LIST_ROW_TO_CREATE_GAP_Y;
        int importY = createRowY + LIST_ROW_H + LIST_ROW_TO_CREATE_GAP_Y;
        int exportY = importY + LIST_ROW_H + LIST_ROW_TO_CREATE_GAP_Y;
        int sortY = exportY + LIST_ROW_H - SORT_BTN_H;
        int searchY = sortY + SORT_BTN_H + SORT_TO_SEARCH_GAP_Y;
        return searchY + SEARCH_BOX_H + SEARCH_TO_LIST_GAP_Y;
    }

    private int categoryYNatural() {
        int entryY = listTopNatural() + LIST_HEIGHT + LIST_TO_ENTRY_GAP_Y;
        return entryY + ENTRY_ROW_H + ENTRY_TO_CATEGORY_GAP_Y;
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

    private void renderInventorySlotIcon(GuiGraphics graphics, net.minecraft.world.entity.player.Player player, int slotIndex, int x, int y) {
        ItemStack stack = player.getInventory().getItem(slotIndex);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
        }
    }

    private void renderRow(GuiGraphics graphics, int x, int y, String id, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + PANEL_WIDTH && mouseY >= y && mouseY < y + ROW_H;
        if (hovered) {
            graphics.fill(x, y, x + PANEL_WIDTH, y + ROW_H, 0x40FFFFFF);
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
        if (item != null) {
            graphics.renderItem(new ItemStack(item), x + 2, y);
        }
        String name = item != null ? Component.translatable(item.getDescriptionId()).getString() : id;
        PriceEntry entry = currentState().workingPrices.get(id);
        if (entry != null && entry.category() != null && !entry.category().isBlank()) {
            name = name + " [" + entry.category() + "]";
        }
        String sellPriceText = entry != null && entry.sellPrice() > 0 ? CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(entry.sellPrice())) : "-";
        String buyPriceText = entry != null && entry.buyPrice() > 0 ? CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(entry.buyPrice())) : "-";
        graphics.drawString(this.font, name, x + NAME_GAP_X + ICON_SIZE, y + 4, 0xFFFFFF, false);

        // Nutzer-Vorgabe (getrennte Preise, ohne separate An/Aus-Buchstaben - "A:"/"V:" reicht):
        // "A:" = Ankaufspreis (Merchant kauft dem Spieler ab), "V:" = Verkaufspreis (Spieler
        // bestellt am Ticker).
        String priceText = "A:" + sellPriceText + " V:" + buyPriceText;
        graphics.drawString(this.font, priceText, x + PANEL_WIDTH - PRICE_COLUMN_W - ROW_RESET_COL_W, y + 4,
            entry != null && (entry.sellPrice() > 0 || entry.buyPrice() > 0) ? 0x55FF55 : 0x808080, false);

        // Nutzer-Vorgabe: rotes X am Zeilenende setzt Preise+Kategorie dieses Items komplett zurück.
        boolean resetHovered = mouseX >= x + PANEL_WIDTH - ROW_RESET_COL_W && mouseX < x + PANEL_WIDTH
            && mouseY >= y && mouseY < y + ROW_H;
        graphics.drawString(this.font, "x", x + PANEL_WIDTH - ROW_RESET_COL_W + 3, y + 4,
            resetHovered ? 0xFFAAAA : 0xFF5555, false);
    }

    @Override
    protected boolean mouseClickedScaled(double mouseX, double mouseY, int button) {
        // Dropdowns/Popups haben Vorrang vor allem anderen, solange sie offen sind.
        if (linkedActionUuid != null) {
            return handleLeftPanelClick(mouseX, mouseY, button);
        }

        if (playerPickerOpen) {
            List<String> suggestions = playerPickerSuggestions();
            int x = playerPickerPopupX;
            int y = playerPickerPopupY + PLAYER_PICKER_BOX_H;
            if (!suggestions.isEmpty() && mouseX >= x && mouseX < x + PLAYER_PICKER_BOX_W && mouseY >= y && mouseY < y + suggestions.size() * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - y) / SUGGEST_ROW_H);
                if (index >= 0 && index < suggestions.size()) {
                    choosePlayerPickerSuggestion(suggestions.get(index));
                    return true;
                }
            }
            if (mouseX >= playerPickerPopupX && mouseX < playerPickerPopupX + PLAYER_PICKER_BOX_W
                    && mouseY >= playerPickerPopupY && mouseY < playerPickerPopupY + PLAYER_PICKER_BOX_H) {
                return super.mouseClickedScaled(mouseX, mouseY, button);
            }
            closePlayerPicker();
            return true;
        }

        if (saleModeDropdownOpen) {
            int x = saleModeButton.getX() + saleModeButton.getWidth() + DROPDOWN_RIGHT_GAP_X;
            int y = saleModeButton.getY();
            if (mouseX >= x && mouseX < x + RECIPIENT_MODE_POPUP_W && mouseY >= y && mouseY < y + 3 * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - y) / SUGGEST_ROW_H);
                saleModeDropdownOpen = false;
                if (index == 0) {
                    sendSaleModeUpdate(CobbleMerchantPayoutManager.MODE_NONE, null);
                } else if (index == 1) {
                    openPlayerPicker(null, x, y);
                } else if (index == 2) {
                    sendSaleModeUpdate(CobbleMerchantPayoutManager.MODE_VARIES, null);
                }
                return true;
            }
            saleModeDropdownOpen = false;
        }

        if (filterCategoryDropdownOpen) {
            List<String> options = new ArrayList<>();
            options.add(Component.translatable("cobblecompanion.gui.stockticker.filter_category_none").getString());
            options.addAll(knownCategoriesForFilter());
            int x = filterCategoryButton.getX() + filterCategoryButton.getWidth() + DROPDOWN_RIGHT_GAP_X;
            int y = filterCategoryButton.getY();
            if (mouseX >= x && mouseX < x + RECIPIENT_MODE_POPUP_W && mouseY >= y && mouseY < y + options.size() * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - y) / SUGGEST_ROW_H);
                if (index >= 0 && index < options.size()) {
                    filterCategory = index == 0 ? "" : options.get(index);
                    rebuildSelectableIdsAndResetScroll();
                }
                filterCategoryDropdownOpen = false;
                return true;
            }
            filterCategoryDropdownOpen = false;
        }

        if (sortDropdownOpen) {
            SortMode[] modes = SortMode.values();
            int x = sortModeButton.getX();
            int y = sortModeButton.getY() + sortModeButton.getHeight();
            if (mouseX >= x && mouseX < x + SORT_BTN_W && mouseY >= y && mouseY < y + modes.length * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - y) / SUGGEST_ROW_H);
                if (index >= 0 && index < modes.length) {
                    switchSortMode(modes[index]);
                    return true;
                }
            }
            sortDropdownOpen = false;
        }

        if (listDropdownOpen) {
            List<String> ids = new ArrayList<>(listStates.keySet());
            int x = listSelectButton.getX();
            int y = listSelectButton.getY() + listSelectButton.getHeight();
            if (mouseX >= x && mouseX < x + LIST_SELECT_BTN_W && mouseY >= y && mouseY < y + ids.size() * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - y) / SUGGEST_ROW_H);
                if (index >= 0 && index < ids.size()) {
                    switchToList(ids.get(index));
                    return true;
                }
            }
            listDropdownOpen = false;
        }

        if (itemIdBox.isFocused()) {
            List<String> suggestions = itemIdSuggestions();
            int x = itemIdBox.getX();
            int y = itemIdBox.getY() + itemIdBox.getHeight();
            if (!suggestions.isEmpty() && mouseX >= x && mouseX < x + ITEMID_BOX_W && mouseY >= y && mouseY < y + suggestions.size() * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - y) / SUGGEST_ROW_H);
                if (index >= 0 && index < suggestions.size()) {
                    loadIntoForm(suggestions.get(index));
                    itemIdBox.setCursorPosition(suggestions.get(index).length());
                    return true;
                }
            }
        }

        if (categoryBox.isFocused()) {
            List<String> suggestions = categorySuggestions();
            int x = categoryBox.getX();
            int y = categoryBox.getY() + categoryBox.getHeight();
            if (!suggestions.isEmpty() && mouseX >= x && mouseX < x + CATEGORY_BOX_W && mouseY >= y && mouseY < y + suggestions.size() * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - y) / SUGGEST_ROW_H);
                if (index >= 0 && index < suggestions.size()) {
                    categoryBox.setValue(suggestions.get(index));
                    categoryBox.setCursorPosition(suggestions.get(index).length());
                    return true;
                }
            }
        }

        int listTop = sy(listTopNatural());
        int listBottom = listTop + LIST_HEIGHT;
        int panelX = (this.width - PANEL_WIDTH) / 2;
        if (mouseX >= panelX && mouseX < panelX + PANEL_WIDTH && mouseY >= listTop && mouseY < listBottom) {
            int index = (int) ((mouseY - listTop + scrollAmount) / ROW_H);
            if (index >= 0 && index < selectableIds.size()) {
                if (mouseX >= panelX + PANEL_WIDTH - ROW_RESET_COL_W) {
                    resetRow(selectableIds.get(index));
                } else {
                    selectRow(selectableIds.get(index));
                }
                return true;
            }
        }

        if (handleLeftPanelClick(mouseX, mouseY, button)) return true;

        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    @Override
    protected boolean mouseScrolledScaled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listTop = sy(listTopNatural());
        int listBottom = listTop + LIST_HEIGHT;
        if (mouseY >= listTop && mouseY < listBottom) {
            int maxScroll = Math.max(0, selectableIds.size() * ROW_H - LIST_HEIGHT);
            scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount - scrollY * ROW_H * 3));
            return true;
        }

        if (showLeftPanel() && mouseX >= leftPanelX && mouseX < leftPanelX + leftPanelWidth
                && mouseY >= linkedPanelTop() && mouseY < linkedPanelBottom()) {
            int maxScroll = Math.max(0, linkedEntries.size() * LEFT_ROW_H - (linkedPanelBottom() - linkedPanelTop()));
            linkedPanelScroll = Math.max(0, Math.min(maxScroll, linkedPanelScroll - scrollY * LEFT_ROW_H));
            return true;
        }

        if (maxPageScroll > 0) {
            pageScroll = Math.max(0, Math.min(maxPageScroll, pageScroll - scrollY * ROW_H * 2));
            applyPageScroll();
            return true;
        }
        return super.mouseScrolledScaled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
