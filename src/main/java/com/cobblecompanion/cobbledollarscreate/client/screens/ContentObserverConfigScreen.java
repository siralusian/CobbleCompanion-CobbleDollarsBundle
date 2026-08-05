package com.cobblecompanion.cobbledollarscreate.client.screens;

import com.cobblecompanion.client.data.ClientNetworkUtil;
import com.cobblecompanion.client.screens.FixedScaleScreen;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientKnownPlayersHelper;
import com.cobblecompanion.cobbledollarscreate.network.ContentObserverConfigSyncPacket;
import com.cobblecompanion.cobbledollarscreate.network.ContentObserverConfigUpdatePacket;
import com.cobblecompanion.cobbledollarscreate.network.ContentObserverNetworkPriceUpdatePacket;
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Editor für den "Schlauen Beobachter" (create:content_observer), geöffnet per Strg+Rechtsklick
 * (echter Minecraft-OP, siehe ContentObserverInteractionHandler).
 *
 * Nutzer-Vorgabe (GUI-Layout, 3 Spalten): links die Liste(n) bereits konfigurierter Regeln, mittig
 * das Bearbeitungsformular (Item/Empfänger/Preise), rechts oben das Gruppen-Panel (Name/
 * Verzögerung/Mitgliederzahlen/Verlassen), rechts unten das Lagernetzwerk-Info-Panel. Links-/
 * Rechts-Spalte sitzen nah am Bildschirmrand, die mittlere Spalte bleibt zentriert.
 *
 * Erweiterung (Nutzer-Vorgabe, 3. Live-Test, "geteilte Katalog-Liste ohne Block-Rolle"): ist der
 * Block Teil einer Gruppe, zeigt die linke Spalte ZWEI unabhängige, scrollbare Listen - oben, was
 * ZÄHLER erfassen sollen, unten, was ABZIEHER erfassen sollen (siehe
 * ContentObserverGroupCatalogManager) - BEIDE Listen sind immer sichtbar, unabhängig davon, was
 * dieser Block gerade tut. Ein Katalog-Eintrag kann in KEINER, EINER oder BEIDEN Listen erscheinen
 * (2 Checkboxen im Formular: "In Zähler-Liste"/"In Abzieher-Liste"). Jede Zeile hat zusätzlich eine
 * eigene Aktiv/Inaktiv-Checkbox NUR für diesen Block. Ein Block kann dadurch weiterhin gleichzeitig
 * manche Items zählen und andere abziehen. 2 weitere Checkboxen ("Aktiv setzen (alle)"/"Inaktiv
 * setzen (alle)") erzwingen beim Speichern die Aktiv/Inaktiv-Checkbox für dieses eine Item bei
 * ALLEN Beobachtern der Gruppe - siehe Klassenkommentar von ContentObserverConfigManager.BlockConfig#subtractorBlock
 * dafür, warum "Rolle" (nur zur Bulk-Einordnung, NICHT zur Checkbox-Einschränkung) zurückgekehrt ist.
 * Ohne Gruppe (groupId leer) unverändertes Alt-Verhalten: eine einzelne, unabhängige Liste ohne
 * Checkboxen.
 *
 * Bugfix (Nutzer-Fund, 4. Live-Test): "Hinzufügen" und "Aktualisieren" sind jetzt getrennte Buttons
 * (vorher ein gemeinsamer "Hinzufügen/Aktualisieren"-Button, der bei geladenem Eintrag IMMER
 * überschrieb) - so lässt sich ein bestehender Eintrag als Vorlage laden, abändern und als NEUER,
 * separater Katalog-Eintrag hinzufügen, ohne das Original zu verlieren.
 *
 * Alle Positions-/Größen-Konstanten sind bewusst einzeln benannt (JUSTIERSCHRAUBE-Muster dieses
 * Projekts) - Pixel-Feinschliff ist ohne Ingame-Test nicht möglich, siehe feedback_justierschrauben.
 */
public class ContentObserverConfigScreen extends FixedScaleScreen {

    private static void sendToServer(CustomPacketPayload payload) {
        if (ClientNetworkUtil.canSendToServerOrWarn(payload.type().id())) {
            PacketDistributor.sendToServer(payload);
        }
    }

    // ===== JUSTIERSCHRAUBEN: Spalten-Layout =====
    private static final int LEFT_COL_W = 190;
    private static final int MIDDLE_COL_W = 190;
    private static final int RIGHT_COL_W = 180;
    private static final int SCREEN_EDGE_MARGIN = 10;
    private static final int TOP_Y = 32;

    // ===== JUSTIERSCHRAUBEN: linke Spalte, UNGRUPPIERT (eine flache Liste, wie bisher) =====
    private static final int LIST_ROW_H = 16;
    private static final int UNGROUPED_LIST_MAX_ROWS = 9;
    private static final int LIST_REMOVE_COL_W = 12;

    // ===== JUSTIERSCHRAUBEN: linke Spalte, GRUPPIERT (2 Listen mit Scrollbar) =====
    // Nutzer-Vorgabe (4. Live-Test): Abzieher-Liste sitzt fest auf halber Bildschirmhöhe, beide
    // Listen füllen die jeweils verfügbare Höhe komplett aus (Zeilenzahl also dynamisch je Auflösung,
    // siehe initScaled/counterVisibleRows/subtractorVisibleRows) statt einer festen Zeilenzahl.
    private static final int LIST_HEADER_H = 12;
    private static final int LIST_TO_HALF_GAP_Y = 6;
    private static final int LIST_CHECKBOX_W = 10;
    private static final int LIST_CHECKBOX_GAP_X = 3;
    private static final int LIST_SCROLLBAR_W = 4;
    /** Nutzer-Vorgabe: etwas dunklerer, halbtransparenter Hintergrund hinter jeder Liste. */
    private static final int LIST_BG_PADDING = 3;
    private static final int LIST_BG_COLOR = 0x80101010;

    // ===== JUSTIERSCHRAUBEN: mittlere Spalte (Formular) =====
    private static final int ROW_H = 22;
    private static final int LABEL_GAP_Y = 12;
    private static final int FIELD_H = 18;
    private static final int PRICE_FIELD_W = 80;
    private static final int USE_PRICE_BTN_W = 91;
    private static final int USE_PRICE_BTN_H = 16;
    private static final int USE_PRICE_BTN_GAP_X = 8;
    private static final int USE_PRICE_TO_FORM_CHECKBOX_GAP_Y = 10;
    /** Nutzer-Vorgabe (geteilte Liste): "In Zähler-Liste"/"In Abzieher-Liste"/"Aktiv setzen (alle)"/"Inaktiv setzen (alle)" - nur sichtbar/aktiv, wenn der Block gruppiert ist. */
    private static final int FORM_CHECKBOX_SIZE = 10;
    private static final int FORM_CHECKBOX_ROW_H = 14;
    private static final int FORM_CHECKBOX_ROW_GAP_Y = 5;
    private static final int FORM_CHECKBOX_COL_W = (MIDDLE_COL_W - 8) / 2;
    private static final int FORM_CHECKBOX_COL_GAP_X = 8;
    private static final int FORM_CHECKBOX_TO_APPLY_GAP_Y = 10;
    private static final int APPLY_BTN_H = 20;
    private static final int SUGGEST_ROW_H = 14;
    private static final int MAX_ITEMID_SUGGESTIONS = 6;
    private static final int MAX_PLAYER_SUGGESTIONS = 8;
    private static final int SUGGEST_GAP_X = 6;
    private static final int SUGGEST_BOX_W = 150;
    private static final int FORM_TO_INV_GAP_Y = 14;
    private static final int INV_SLOT_SIZE = 18;
    private static final int INV_COLUMNS = 9;
    private static final int INV_MAIN_ROWS = 3;
    private static final int INV_ROW_GAP_Y = 4;
    private static final int INV_LABEL_GAP_Y = 12;

    // ===== JUSTIERSCHRAUBEN: rechte Spalte oben (Gruppen-Panel) =====
    private static final int GROUP_TITLE_GAP_Y = 14;
    private static final int GROUP_ROW_H = 22;
    private static final int GROUP_LABEL_GAP_Y = 11;
    private static final int GROUP_LABEL_TO_FIELD_GAP_Y = 12;
    private static final int GROUP_FIELD_H = 16;
    private static final int GROUP_COUNTS_GAP_Y = 4;
    private static final int GROUP_LEAVE_GAP_Y = 10;
    private static final int GROUP_LEAVE_BTN_H = 18;

    // ===== JUSTIERSCHRAUBEN: rechte Spalte unten (Netzwerk-Info-Panel) =====
    private static final int NETWORK_PANEL_GAP_Y = 26;
    private static final int NETWORK_PANEL_LINE_GAP_Y = 12;

    // ===== JUSTIERSCHRAUBEN: unten (Speichern/Entfernen/Abbrechen) =====
    private static final int BOTTOM_GAP_Y = 22;
    private static final int BTN_W = 80;
    private static final int BTN_H = 20;
    private static final int BTN_GAP_X = 8;
    /** Platz unterhalb der Abzieher-Liste für Speichern/Entfernen/Abbrechen + kleinen Rand zum Bildschirmrand. */
    private static final int BOTTOM_ROW_RESERVED_H = BOTTOM_GAP_Y + BTN_H + 10;

    private static final class EditableEntry {
        String itemId;
        String targetPlayerName;
        long amountPerItem;
        /** Nur bedeutsam, wenn der Block gruppiert ist - siehe Klassenkommentar. */
        boolean inCounterList;
        boolean inSubtractorList;
        /** Aktiv/Inaktiv-Checkboxen NUR für DIESEN Block, unabhängig pro Liste. */
        boolean enabledAsCounter;
        boolean enabledAsSubtractor;
    }

    private final BlockPos pos;
    private final List<ContentObserverConfigSyncPacket.CatalogEntryView> initialRules;
    private final String initialGroupId;
    private final String initialGroupName;
    private final boolean initialSubtractorBlock;
    private final int initialPromiseExpiryStage;
    private final boolean networkConnected;
    private final String networkListName;
    private final int counterCount;
    private final int subtractorCount;
    private final Map<String, ContentObserverConfigSyncPacket.NetworkPriceView> networkPrices;
    private final Set<String> initialEnabledCounterItemIds;
    private final Set<String> initialEnabledSubtractorItemIds;

    private final List<EditableEntry> entries = new ArrayList<>();
    private EditableEntry selectedEntry;
    private int counterScrollOffset;
    private int subtractorScrollOffset;
    /** Dynamisch je verfügbarer Höhe berechnet (siehe initScaled) - siehe Klassenkommentar an LIST_HEADER_H. */
    private int counterVisibleRows;
    private int subtractorVisibleRows;
    private String groupId;
    private String groupName;
    private boolean subtractorBlock;
    private int promiseExpiryStage;

    // Formular-Zustand für die 4 neuen Checkboxen (siehe Klassenkommentar) - formInCounterList/
    // formInSubtractorList bleiben zwischen Speicherungen bewusst erhalten (man tippt oft mehrere
    // Abzieher-Items hintereinander, ohne die Checkbox jedes Mal neu setzen zu wollen), die beiden
    // Bulk-Checkboxen werden dagegen nach jeder Anwendung zurückgesetzt (einmalige, bewusste Aktion).
    private boolean formInCounterList = true;
    private boolean formInSubtractorList;
    private boolean formBulkOn;
    private boolean formBulkOff;
    /** Item + gewünschte Bulk-Aktion aus dem LETZTEN applyForm()-Aufruf mit gesetzter Bulk-Checkbox - wird beim Speichern mitgeschickt (siehe ContentObserverConfigUpdatePacket). */
    private String pendingBulkItemId = "";
    private String pendingBulkAction = "";

    private int leftColX;
    private int middleColX;
    private int rightColX;
    private int listTop; // ungruppiert
    private int listBottom; // ungruppiert
    private int counterPanelTop; // gruppiert
    private int subtractorPanelTop; // gruppiert
    private int leftColumnBottom;
    private int invTop;
    private int invX;
    private int hotbarY;
    private int groupPanelTop;
    private int networkPanelTop;
    private int formInCounterCheckboxX, formInCounterCheckboxY;
    private int formInSubtractorCheckboxX, formInSubtractorCheckboxY;
    private int formBulkOnCheckboxX, formBulkOnCheckboxY;
    private int formBulkOffCheckboxX, formBulkOffCheckboxY;

    private EditBox itemIdBox;
    private EditBox targetPlayerBox;
    private EditBox amountBox;
    private EditBox ankaufBox;
    private EditBox verkaufBox;
    private EditBox groupNameBox;
    private Button leaveGroupButton;
    private Button expiryButton;
    private Button roleButton;
    private Button updateButton;

    public ContentObserverConfigScreen(BlockPos pos, List<ContentObserverConfigSyncPacket.CatalogEntryView> rules,
            String groupId, String groupName, boolean subtractorBlock, int promiseExpiryStage, boolean networkConnected,
            String networkListName, int counterCount, int subtractorCount,
            Map<String, ContentObserverConfigSyncPacket.NetworkPriceView> networkPrices,
            Set<String> enabledCounterItemIds, Set<String> enabledSubtractorItemIds) {
        super(Component.translatable("cobblecompanion.gui.contentobserver.title"));
        this.pos = pos;
        this.initialRules = rules;
        this.initialGroupId = groupId;
        this.initialGroupName = groupName;
        this.initialSubtractorBlock = subtractorBlock;
        this.initialPromiseExpiryStage = promiseExpiryStage;
        this.networkConnected = networkConnected;
        this.networkListName = networkListName;
        this.counterCount = counterCount;
        this.subtractorCount = subtractorCount;
        this.networkPrices = networkPrices;
        this.initialEnabledCounterItemIds = enabledCounterItemIds != null ? enabledCounterItemIds : Set.of();
        this.initialEnabledSubtractorItemIds = enabledSubtractorItemIds != null ? enabledSubtractorItemIds : Set.of();
    }

    private boolean grouped() {
        return !groupId.isBlank();
    }

    @Override
    protected void initScaled() {
        groupId = initialGroupId == null ? "" : initialGroupId;
        groupName = initialGroupName == null ? "" : initialGroupName;
        subtractorBlock = initialSubtractorBlock;
        promiseExpiryStage = initialPromiseExpiryStage;
        selectedEntry = null;
        counterScrollOffset = 0;
        subtractorScrollOffset = 0;
        formInCounterList = true;
        formInSubtractorList = false;
        formBulkOn = false;
        formBulkOff = false;
        pendingBulkItemId = "";
        pendingBulkAction = "";

        boolean grouped = grouped();
        entries.clear();
        for (ContentObserverConfigSyncPacket.CatalogEntryView view : initialRules) {
            EditableEntry e = new EditableEntry();
            e.itemId = view.itemId();
            e.targetPlayerName = view.targetPlayerName();
            e.amountPerItem = view.amountPerItem();
            e.inCounterList = view.inCounterList();
            e.inSubtractorList = view.inSubtractorList();
            e.enabledAsCounter = grouped && initialEnabledCounterItemIds.contains(e.itemId);
            e.enabledAsSubtractor = grouped && initialEnabledSubtractorItemIds.contains(e.itemId);
            entries.add(e);
        }

        leftColX = SCREEN_EDGE_MARGIN;
        rightColX = this.width - SCREEN_EDGE_MARGIN - RIGHT_COL_W;
        middleColX = (this.width - MIDDLE_COL_W) / 2;

        if (grouped) {
            // Nutzer-Vorgabe (4. Live-Test): Abzieher-Liste fest auf halber Bildschirmhöhe, beide
            // Listen füllen ihren jeweils verfügbaren Bereich maximal aus.
            counterPanelTop = TOP_Y;
            subtractorPanelTop = this.height / 2;
            int counterListBottom = subtractorPanelTop - LIST_TO_HALF_GAP_Y;
            int subtractorListBottom = this.height - BOTTOM_ROW_RESERVED_H;
            counterVisibleRows = Math.max(1, (counterListBottom - counterPanelTop - LIST_HEADER_H) / LIST_ROW_H);
            subtractorVisibleRows = Math.max(1, (subtractorListBottom - subtractorPanelTop - LIST_HEADER_H) / LIST_ROW_H);
            leftColumnBottom = subtractorListBottom;
        } else {
            listTop = TOP_Y;
            listBottom = listTop + UNGROUPED_LIST_MAX_ROWS * LIST_ROW_H;
            leftColumnBottom = listBottom;
        }

        buildMiddleColumn();
        buildRightColumn();

        int bottomY = Math.max(leftColumnBottom, Math.max(invBottomY(), groupPanelBottom())) + BOTTOM_GAP_Y;
        int panelCenterX = this.width / 2;
        addRenderableWidget(Button.builder(Component.translatable("cobblecompanion.gui.contentobserver.save"), b -> save())
            .bounds(panelCenterX - BTN_W - BTN_GAP_X - BTN_W / 2, bottomY, BTN_W, BTN_H)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("cobblecompanion.gui.contentobserver.clear"), b -> clear())
            .bounds(panelCenterX - BTN_W / 2, bottomY, BTN_W, BTN_H)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(panelCenterX + BTN_GAP_X + BTN_W / 2, bottomY, BTN_W, BTN_H)
            .build());
    }

    private void buildMiddleColumn() {
        int y = TOP_Y;
        boolean grouped = grouped();

        itemIdBox = new EditBox(this.font, middleColX, y + LABEL_GAP_Y, MIDDLE_COL_W, FIELD_H,
            Component.translatable("cobblecompanion.gui.contentobserver.item_hint"));
        itemIdBox.setHint(Component.translatable("cobblecompanion.gui.contentobserver.item_hint"));
        itemIdBox.setMaxLength(256);
        addRenderableWidget(itemIdBox);
        y += ROW_H + LABEL_GAP_Y;

        targetPlayerBox = new EditBox(this.font, middleColX, y + LABEL_GAP_Y, MIDDLE_COL_W, FIELD_H,
            Component.translatable("cobblecompanion.gui.contentobserver.player_hint"));
        targetPlayerBox.setHint(Component.translatable("cobblecompanion.gui.contentobserver.player_hint"));
        targetPlayerBox.setMaxLength(64);
        addRenderableWidget(targetPlayerBox);
        y += ROW_H + LABEL_GAP_Y;

        amountBox = new EditBox(this.font, middleColX, y + LABEL_GAP_Y, PRICE_FIELD_W, FIELD_H, Component.empty());
        amountBox.setMaxLength(15);
        addRenderableWidget(amountBox);
        y += ROW_H + LABEL_GAP_Y;

        ankaufBox = new EditBox(this.font, middleColX, y + LABEL_GAP_Y, PRICE_FIELD_W, FIELD_H, Component.empty());
        ankaufBox.setMaxLength(15);
        ankaufBox.setEditable(networkConnected);
        addRenderableWidget(ankaufBox);
        y += ROW_H + LABEL_GAP_Y;

        verkaufBox = new EditBox(this.font, middleColX, y + LABEL_GAP_Y, PRICE_FIELD_W, FIELD_H, Component.empty());
        verkaufBox.setMaxLength(15);
        verkaufBox.setEditable(networkConnected);
        addRenderableWidget(verkaufBox);
        y += ROW_H + LABEL_GAP_Y;

        Button useAnkaufButton = Button.builder(Component.translatable("cobblecompanion.gui.contentobserver.use_ankauf"),
                b -> amountBox.setValue(ankaufBox.getValue()))
            .bounds(middleColX, y, USE_PRICE_BTN_W, USE_PRICE_BTN_H)
            .build();
        addRenderableWidget(useAnkaufButton);
        Button useVerkaufButton = Button.builder(Component.translatable("cobblecompanion.gui.contentobserver.use_verkauf"),
                b -> amountBox.setValue(verkaufBox.getValue()))
            .bounds(middleColX + USE_PRICE_BTN_W + USE_PRICE_BTN_GAP_X, y, USE_PRICE_BTN_W, USE_PRICE_BTN_H)
            .build();
        addRenderableWidget(useVerkaufButton);
        y += USE_PRICE_BTN_H + USE_PRICE_TO_FORM_CHECKBOX_GAP_Y;

        if (grouped) {
            formInCounterCheckboxX = middleColX;
            formInCounterCheckboxY = y;
            formInSubtractorCheckboxX = middleColX + FORM_CHECKBOX_COL_W + FORM_CHECKBOX_COL_GAP_X;
            formInSubtractorCheckboxY = y;
            y += FORM_CHECKBOX_ROW_H + FORM_CHECKBOX_ROW_GAP_Y;

            formBulkOnCheckboxX = middleColX;
            formBulkOnCheckboxY = y;
            formBulkOffCheckboxX = middleColX + FORM_CHECKBOX_COL_W + FORM_CHECKBOX_COL_GAP_X;
            formBulkOffCheckboxY = y;
            y += FORM_CHECKBOX_ROW_H + FORM_CHECKBOX_TO_APPLY_GAP_Y;
        }

        addRenderableWidget(Button.builder(Component.translatable("cobblecompanion.gui.contentobserver.add"), b -> addEntry())
            .bounds(middleColX, y, FORM_CHECKBOX_COL_W, APPLY_BTN_H)
            .build());
        updateButton = Button.builder(Component.translatable("cobblecompanion.gui.contentobserver.update"), b -> updateEntry())
            .bounds(middleColX + FORM_CHECKBOX_COL_W + FORM_CHECKBOX_COL_GAP_X, y, FORM_CHECKBOX_COL_W, APPLY_BTN_H)
            .build();
        updateButton.active = selectedEntry != null;
        addRenderableWidget(updateButton);
        y += APPLY_BTN_H + FORM_TO_INV_GAP_Y;

        invTop = y + INV_LABEL_GAP_Y;
        invX = middleColX;
        hotbarY = invTop + INV_MAIN_ROWS * INV_SLOT_SIZE + INV_ROW_GAP_Y;
    }

    private int invBottomY() {
        return hotbarY + INV_SLOT_SIZE;
    }

    private void buildRightColumn() {
        int y = TOP_Y;
        boolean grouped = grouped();

        y += GROUP_TITLE_GAP_Y;
        if (grouped) {
            groupNameBox = new EditBox(this.font, rightColX, y, RIGHT_COL_W, GROUP_FIELD_H,
                Component.translatable("cobblecompanion.gui.contentobserver.group_name_hint"));
            groupNameBox.setMaxLength(48);
            groupNameBox.setValue(groupName);
            addRenderableWidget(groupNameBox);
        }
        y += GROUP_FIELD_H + GROUP_LABEL_GAP_Y;

        y += GROUP_LABEL_TO_FIELD_GAP_Y; // Platz für "Verzögerte Auszahlung"-Label
        expiryButton = Button.builder(expiryButtonLabel(), b -> cycleExpiry(1))
            .bounds(rightColX, y, RIGHT_COL_W, GROUP_FIELD_H)
            .build();
        expiryButton.active = grouped;
        addRenderableWidget(expiryButton);
        y += GROUP_FIELD_H + GROUP_LABEL_GAP_Y;

        y += GROUP_LABEL_TO_FIELD_GAP_Y; // Platz für "Rolle"-Label
        roleButton = Button.builder(roleButtonLabel(), b -> {
                // Nutzer-Vorgabe (4. Live-Test): reine Klassifizierung für "Aktiv/Inaktiv für alle
                // setzen" - schränkt KEINE Checkbox ein, siehe Klassenkommentar.
                subtractorBlock = !subtractorBlock;
                roleButton.setMessage(roleButtonLabel());
            })
            .bounds(rightColX, y, RIGHT_COL_W, GROUP_FIELD_H)
            .build();
        roleButton.active = grouped;
        addRenderableWidget(roleButton);
        y += GROUP_FIELD_H + GROUP_COUNTS_GAP_Y;

        y += GROUP_ROW_H; // Platz für die Zähler/Abzieher-Mitgliederzahlen (reines Text-Rendering, siehe renderScaled)
        y += GROUP_LEAVE_GAP_Y;

        leaveGroupButton = Button.builder(Component.translatable("cobblecompanion.gui.contentobserver.leave_group"), b -> {
                // Nutzer-Vorgabe: beim Verlassen der Gruppe werden nur die für DIESEN Block (in
                // irgendeiner der beiden Listen) aktivierten Katalog-Items in eine eigene,
                // unabhängige Liste übernommen - kein Datenverlust für diesen Block, aber auch keine
                // fremden/unaktivierten Katalog-Items anderer Gruppenmitglieder.
                entries.removeIf(e -> !e.enabledAsCounter && !e.enabledAsSubtractor);
                groupId = "";
                selectedEntry = null;
                clearForm();
                leaveGroupButton.active = false;
                expiryButton.active = false;
                roleButton.active = false;
                if (groupNameBox != null) groupNameBox.setValue("");
            })
            .bounds(rightColX, y, RIGHT_COL_W, GROUP_LEAVE_BTN_H)
            .build();
        leaveGroupButton.active = grouped;
        addRenderableWidget(leaveGroupButton);
        y += GROUP_LEAVE_BTN_H;

        groupPanelTop = TOP_Y;
        networkPanelTop = y + NETWORK_PANEL_GAP_Y;
    }

    private int groupPanelBottom() {
        return networkPanelTop + NETWORK_PANEL_LINE_GAP_Y * 3;
    }

    private Component roleButtonLabel() {
        return Component.translatable(subtractorBlock
            ? "cobblecompanion.gui.contentobserver.role_subtractor"
            : "cobblecompanion.gui.contentobserver.role_counter");
    }

    private Component expiryButtonLabel() {
        String value = promiseExpiryStage < 0 ? "∞" : (promiseExpiryStage == 0 ? "30s" : promiseExpiryStage + "m");
        return Component.literal(value);
    }

    private void cycleExpiry(int direction) {
        promiseExpiryStage = Math.max(-1, Math.min(31, promiseExpiryStage + direction));
        expiryButton.setMessage(expiryButtonLabel());
    }

    /** Nutzer-Vorgabe (4. Live-Test): erstellt IMMER einen NEUEN, unabhängigen Katalog-Eintrag aus den aktuellen Formularwerten - auch wenn gerade ein Eintrag als Vorlage geladen ist (der bleibt dabei unangetastet). Für Änderungen AM geladenen Eintrag siehe {@link #updateEntry()}. */
    private void addEntry() {
        boolean grouped = grouped();
        EditableEntry entry = new EditableEntry();
        readFormInto(entry);
        if (grouped) {
            // Nutzer-Vorgabe: ein frisch zum Katalog hinzugefügtes Item wird für DIESEN Block
            // sofort in der/den Liste(n) aktiviert, in die es eingeordnet wird.
            entry.enabledAsCounter = formInCounterList;
            entry.enabledAsSubtractor = formInSubtractorList;
        }
        entries.add(entry);
        writeNetworkPriceIfApplicable(entry.itemId);
        clearForm();
    }

    /** Nutzer-Vorgabe (4. Live-Test): ändert NUR den aktuell geladenen Eintrag in place - fügt NICHTS Neues hinzu. Ohne geladenen Eintrag (Button dann inaktiv) passiert nichts. */
    private void updateEntry() {
        if (selectedEntry == null) return;
        readFormInto(selectedEntry);
        writeNetworkPriceIfApplicable(selectedEntry.itemId);
        clearForm();
    }

    private void readFormInto(EditableEntry entry) {
        entry.itemId = itemIdBox.getValue().trim();
        entry.targetPlayerName = targetPlayerBox.getValue().trim();
        BigInteger parsedAmount = CobbleDollarsScale.parseToRaw(amountBox.getValue().trim());
        entry.amountPerItem = parsedAmount != null ? parsedAmount.longValueExact() : 0;
        if (grouped()) {
            entry.inCounterList = formInCounterList;
            entry.inSubtractorList = formInSubtractorList;
            if (formBulkOn || formBulkOff) {
                pendingBulkItemId = entry.itemId;
                pendingBulkAction = formBulkOn ? "ON" : "OFF";
            }
        }
    }

    /** Nutzer-Vorgabe: Ankaufs-/Verkaufspreis-Zeilen schreiben bei angeschlossenem Lagerverbinder/-ticker direkt ins Netzwerk zurück (nicht nur lokal in dieser Regel). */
    private void writeNetworkPriceIfApplicable(String rawItem) {
        if (!networkConnected || rawItem.isBlank() || rawItem.equals("*") || rawItem.startsWith("#") || rawItem.endsWith(":*")) return;
        String itemId = rawItem.contains(":") ? rawItem : "minecraft:" + rawItem;
        BigInteger ankauf = CobbleDollarsScale.parseToRaw(ankaufBox.getValue().trim());
        BigInteger verkauf = CobbleDollarsScale.parseToRaw(verkaufBox.getValue().trim());
        sendToServer(new ContentObserverNetworkPriceUpdatePacket(pos, itemId,
            ankauf != null ? ankauf.longValueExact() : 0, verkauf != null ? verkauf.longValueExact() : 0));
    }

    private void clearForm() {
        selectedEntry = null;
        itemIdBox.setValue("");
        targetPlayerBox.setValue("");
        amountBox.setValue("");
        ankaufBox.setValue("");
        verkaufBox.setValue("");
        formBulkOn = false;
        formBulkOff = false;
        if (updateButton != null) updateButton.active = false;
    }

    private void loadIntoForm(EditableEntry entry) {
        selectedEntry = entry;
        itemIdBox.setValue(entry.itemId);
        targetPlayerBox.setValue(entry.targetPlayerName);
        amountBox.setValue(entry.amountPerItem != 0 ? CobbleDollarsScale.formatRaw(BigInteger.valueOf(entry.amountPerItem)) : "");
        formInCounterList = entry.inCounterList;
        formInSubtractorList = entry.inSubtractorList;
        formBulkOn = false;
        formBulkOff = false;
        if (updateButton != null) updateButton.active = true;
        updateNetworkPriceFieldsFor(entry.itemId);
    }

    /** Nutzer-Vorgabe: Ankaufs-/Verkaufszeilen zeigen live den Netzwerkpreis des aktuell im Item-Feld stehenden Items - kein Server-Rundlauf, siehe ClientContentObserverHelper. */
    private void updateNetworkPriceFieldsFor(String rawItemId) {
        if (!networkConnected) return;
        String itemId = rawItemId.contains(":") ? rawItemId : "minecraft:" + rawItemId;
        ContentObserverConfigSyncPacket.NetworkPriceView price = networkPrices.get(itemId);
        ankaufBox.setValue(price != null && price.ankaufspreis() != 0 ? CobbleDollarsScale.formatRaw(BigInteger.valueOf(price.ankaufspreis())) : "");
        verkaufBox.setValue(price != null && price.verkaufspreis() != 0 ? CobbleDollarsScale.formatRaw(BigInteger.valueOf(price.verkaufspreis())) : "");
    }

    private void removeEntry(EditableEntry entry) {
        entries.remove(entry);
        if (selectedEntry == entry) clearForm();
    }

    private void save() {
        List<String> encoded = new ArrayList<>();
        List<String> enabledCounterIds = new ArrayList<>();
        List<String> enabledSubtractorIds = new ArrayList<>();
        for (EditableEntry e : entries) {
            encoded.add(ContentObserverConfigSyncPacket.encodeCatalogEntry(e.itemId, e.targetPlayerName, e.amountPerItem, e.inCounterList, e.inSubtractorList));
            if (e.enabledAsCounter) enabledCounterIds.add(e.itemId);
            if (e.enabledAsSubtractor) enabledSubtractorIds.add(e.itemId);
        }
        String finalGroupName = groupNameBox != null ? groupNameBox.getValue().trim() : groupName;
        String groupMeta = ContentObserverConfigUpdatePacket.encodeGroupMeta(groupId, finalGroupName,
            String.join(",", enabledCounterIds), String.join(",", enabledSubtractorIds));
        String meta = ContentObserverConfigUpdatePacket.encodeMeta(promiseExpiryStage, subtractorBlock, pendingBulkItemId, pendingBulkAction);
        sendToServer(new ContentObserverConfigUpdatePacket(pos, false, encoded, groupMeta, meta));
        onClose();
    }

    private void clear() {
        sendToServer(new ContentObserverConfigUpdatePacket(pos, true, List.of(),
            ContentObserverConfigUpdatePacket.encodeGroupMeta("", "", "", ""), ContentObserverConfigUpdatePacket.encodeMeta(0, false, "", "")));
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
        updateNetworkPriceFieldsFor(id);
    }

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

    private int suggestX(EditBox box) {
        return box.getX() + box.getWidth() + SUGGEST_GAP_X;
    }

    private void renderInventorySlotIcon(GuiGraphics graphics, net.minecraft.world.entity.player.Player player, int slotIndex, int x, int y) {
        ItemStack stack = player.getInventory().getItem(slotIndex);
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 1, y + 1);
    }

    private String shortItemLabel(String itemId) {
        if (itemId == null || itemId.isBlank() || itemId.equals("*")) return "*";
        return itemId;
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);

        if (grouped()) {
            renderListPanel(graphics, mouseX, mouseY, true, counterPanelTop);
            renderListPanel(graphics, mouseX, mouseY, false, subtractorPanelTop);
        } else {
            renderUngroupedList(graphics);
        }
        renderMiddleColumn(graphics);
        renderRightColumn(graphics);

        if (itemIdBox.isFocused()) {
            renderSuggestions(graphics, mouseX, mouseY, suggestX(itemIdBox), itemIdBox.getY(), SUGGEST_BOX_W, itemIdSuggestions());
        }
        if (targetPlayerBox.isFocused()) {
            renderSuggestions(graphics, mouseX, mouseY, suggestX(targetPlayerBox), targetPlayerBox.getY(), SUGGEST_BOX_W, playerSuggestions());
        }
    }

    private void renderUngroupedList(GuiGraphics graphics) {
        graphics.fill(leftColX - LIST_BG_PADDING, listTop - LIST_BG_PADDING,
            leftColX + LEFT_COL_W + LIST_BG_PADDING, listBottom + LIST_BG_PADDING, LIST_BG_COLOR);
        if (entries.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.no_rules"),
                leftColX, listTop + 3, 0x808080);
        }
        for (int i = 0; i < entries.size() && i < UNGROUPED_LIST_MAX_ROWS; i++) {
            EditableEntry entry = entries.get(i);
            int rowY = listTop + i * LIST_ROW_H;
            boolean hovered = selectedEntry == entry;
            if (hovered) graphics.fill(leftColX, rowY, leftColX + LEFT_COL_W, rowY + LIST_ROW_H, 0x40FFFFFF);
            renderEntryRow(graphics, entry, leftColX + 2, rowY, LEFT_COL_W - LIST_REMOVE_COL_W - 20);
            graphics.drawString(this.font, "x", leftColX + LEFT_COL_W - LIST_REMOVE_COL_W, rowY + 4, 0xFF5555, false);
        }
    }

    /** Gemeinsames Icon+Text-Rendering einer Katalog-Zeile - Checkbox wird vom Aufrufer separat gezeichnet, siehe renderListPanel. */
    private void renderEntryRow(GuiGraphics graphics, EditableEntry entry, int contentX, int rowY, int textMaxW) {
        Item resolved = !entry.itemId.isBlank() && !entry.itemId.equals("*") && !entry.itemId.startsWith("#") && !entry.itemId.endsWith(":*")
            ? BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(entry.itemId)) : null;
        int textX = contentX;
        if (resolved != null && resolved != net.minecraft.world.item.Items.AIR) {
            graphics.renderItem(new ItemStack(resolved), contentX - 2, rowY);
            textX = contentX + INV_SLOT_SIZE - 2;
        }
        // Nutzer-Fund (4. Live-Test): der Empfänger fehlte in der Zeilen-Anzeige komplett - ohne
        // das Formular zu öffnen, ließ sich nicht sehen, an wen eine Regel überhaupt auszahlt.
        String line = shortItemLabel(entry.itemId) + " " + CobbleDollarsScale.formatRaw(BigInteger.valueOf(entry.amountPerItem));
        if (entry.targetPlayerName != null && !entry.targetPlayerName.isBlank()) {
            line += " → " + entry.targetPlayerName;
        }
        graphics.drawString(this.font, this.font.plainSubstrByWidth(line, textMaxW), textX, rowY + 4, 0xFFFFFF, false);
    }

    private List<EditableEntry> filteredEntries(boolean forCounter) {
        List<EditableEntry> result = new ArrayList<>();
        for (EditableEntry e : entries) {
            if (forCounter ? e.inCounterList : e.inSubtractorList) result.add(e);
        }
        return result;
    }

    private int getScrollOffset(boolean forCounter) {
        return forCounter ? counterScrollOffset : subtractorScrollOffset;
    }

    private void setScrollOffset(boolean forCounter, int value) {
        if (forCounter) counterScrollOffset = value; else subtractorScrollOffset = value;
    }

    private int visibleRows(boolean forCounter) {
        return forCounter ? counterVisibleRows : subtractorVisibleRows;
    }

    private void renderListPanel(GuiGraphics graphics, int mouseX, int mouseY, boolean forCounter, int panelTop) {
        List<EditableEntry> filtered = filteredEntries(forCounter);
        int visibleRows = visibleRows(forCounter);
        int rowsTop = panelTop + LIST_HEADER_H;

        // Nutzer-Vorgabe: etwas dunklerer, halbtransparenter Hintergrund hinter der ganzen Liste - zuerst gezeichnet, damit Text/Checkboxen darüber liegen.
        graphics.fill(leftColX - LIST_BG_PADDING, panelTop - LIST_BG_PADDING,
            leftColX + LEFT_COL_W + LIST_BG_PADDING, rowsTop + visibleRows * LIST_ROW_H + LIST_BG_PADDING, LIST_BG_COLOR);

        Component title = Component.translatable(forCounter
            ? "cobblecompanion.gui.contentobserver.counter_list_title"
            : "cobblecompanion.gui.contentobserver.subtractor_list_title");
        graphics.drawString(this.font, title, leftColX, panelTop, 0xFFFFFF);

        int maxScroll = Math.max(0, filtered.size() - visibleRows);
        int offset = Math.max(0, Math.min(getScrollOffset(forCounter), maxScroll));
        setScrollOffset(forCounter, offset);

        if (filtered.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.no_rules"),
                leftColX, rowsTop + 3, 0x808080);
        }
        int contentX = leftColX + LIST_CHECKBOX_W + LIST_CHECKBOX_GAP_X;
        int textMaxW = LEFT_COL_W - LIST_CHECKBOX_W - LIST_CHECKBOX_GAP_X - LIST_REMOVE_COL_W - LIST_SCROLLBAR_W - 18;
        for (int i = 0; i < visibleRows; i++) {
            int idx = offset + i;
            if (idx >= filtered.size()) break;
            EditableEntry entry = filtered.get(idx);
            int rowY = rowsTop + i * LIST_ROW_H;
            boolean hovered = selectedEntry == entry;
            if (hovered) graphics.fill(leftColX, rowY, leftColX + LEFT_COL_W - LIST_SCROLLBAR_W, rowY + LIST_ROW_H, 0x40FFFFFF);

            int boxY = rowY + (LIST_ROW_H - LIST_CHECKBOX_W) / 2;
            boolean enabled = forCounter ? entry.enabledAsCounter : entry.enabledAsSubtractor;
            graphics.fill(leftColX, boxY, leftColX + LIST_CHECKBOX_W, boxY + LIST_CHECKBOX_W, 0xFF808080);
            if (enabled) graphics.fill(leftColX + 2, boxY + 2, leftColX + LIST_CHECKBOX_W - 2, boxY + LIST_CHECKBOX_W - 2, 0xFF55FF55);

            renderEntryRow(graphics, entry, contentX, rowY, textMaxW);
            graphics.drawString(this.font, "x", leftColX + LEFT_COL_W - LIST_SCROLLBAR_W - LIST_REMOVE_COL_W, rowY + 4, 0xFF5555, false);
        }

        if (filtered.size() > visibleRows) {
            int trackX = leftColX + LEFT_COL_W - LIST_SCROLLBAR_W;
            int trackTop = rowsTop;
            int trackBottom = rowsTop + visibleRows * LIST_ROW_H;
            graphics.fill(trackX, trackTop, trackX + LIST_SCROLLBAR_W, trackBottom, 0x40FFFFFF);
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(6, visibleRows * trackH / filtered.size());
            int thumbY = trackTop + (maxScroll <= 0 ? 0 : offset * (trackH - thumbH) / maxScroll);
            graphics.fill(trackX, thumbY, trackX + LIST_SCROLLBAR_W, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    private void renderMiddleColumn(GuiGraphics graphics) {
        int y = TOP_Y;
        boolean grouped = grouped();
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.item_label"), middleColX, y, 0xA0A0A0);
        y += ROW_H + LABEL_GAP_Y;
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.player_label"), middleColX, y, 0xA0A0A0);
        y += ROW_H + LABEL_GAP_Y;
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.amount_label"), middleColX, y, 0xA0A0A0);
        y += ROW_H + LABEL_GAP_Y;
        int ankaufColor = networkConnected ? 0xA0A0A0 : 0x606060;
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.ankauf_label"), middleColX, y, ankaufColor);
        y += ROW_H + LABEL_GAP_Y;
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.verkauf_label"), middleColX, y, ankaufColor);

        if (grouped) {
            renderFormCheckbox(graphics, formInCounterCheckboxX, formInCounterCheckboxY, formInCounterList,
                Component.translatable("cobblecompanion.gui.contentobserver.in_counter_list_label"));
            renderFormCheckbox(graphics, formInSubtractorCheckboxX, formInSubtractorCheckboxY, formInSubtractorList,
                Component.translatable("cobblecompanion.gui.contentobserver.in_subtractor_list_label"));
            renderFormCheckbox(graphics, formBulkOnCheckboxX, formBulkOnCheckboxY, formBulkOn,
                Component.translatable("cobblecompanion.gui.contentobserver.bulk_active_label"));
            renderFormCheckbox(graphics, formBulkOffCheckboxX, formBulkOffCheckboxY, formBulkOff,
                Component.translatable("cobblecompanion.gui.contentobserver.bulk_inactive_label"));
        }

        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (int row = 0; row < INV_MAIN_ROWS; row++) {
                int rowY = invTop + row * INV_SLOT_SIZE;
                for (int col = 0; col < INV_COLUMNS; col++) {
                    renderInventorySlotIcon(graphics, player, 9 + row * INV_COLUMNS + col, invX + col * INV_SLOT_SIZE, rowY);
                }
            }
            for (int i = 0; i < INV_COLUMNS; i++) {
                renderInventorySlotIcon(graphics, player, i, invX + i * INV_SLOT_SIZE, hotbarY);
            }
        }
    }

    private void renderFormCheckbox(GuiGraphics graphics, int x, int y, boolean checked, Component label) {
        graphics.fill(x, y, x + FORM_CHECKBOX_SIZE, y + FORM_CHECKBOX_SIZE, 0xFF808080);
        if (checked) graphics.fill(x + 2, y + 2, x + FORM_CHECKBOX_SIZE - 2, y + FORM_CHECKBOX_SIZE - 2, 0xFF55FF55);
        graphics.drawString(this.font, label, x + FORM_CHECKBOX_SIZE + 4, y + 1, 0xFFFFFF, false);
    }

    private boolean hitFormCheckbox(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + FORM_CHECKBOX_COL_W && mouseY >= y && mouseY < y + FORM_CHECKBOX_SIZE;
    }

    private void renderRightColumn(GuiGraphics graphics) {
        int y = groupPanelTop;
        boolean grouped = grouped();
        Component groupTitle = grouped
            ? Component.translatable("cobblecompanion.gui.contentobserver.group_name_label")
            : Component.translatable("cobblecompanion.gui.contentobserver.no_group");
        graphics.drawString(this.font, groupTitle, rightColX, y, grouped ? 0xFFFFFF : 0xA0A0A0);
        y += GROUP_TITLE_GAP_Y + GROUP_FIELD_H + GROUP_LABEL_GAP_Y;

        int labelColor = grouped ? 0xA0A0A0 : 0x606060;
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.expiry_label"), rightColX, y, labelColor);
        y += GROUP_LABEL_TO_FIELD_GAP_Y + GROUP_FIELD_H + GROUP_LABEL_GAP_Y;

        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.role_label"), rightColX, y, labelColor);
        y += GROUP_LABEL_TO_FIELD_GAP_Y + GROUP_FIELD_H + GROUP_COUNTS_GAP_Y;

        graphics.drawString(this.font, Component.translatableWithFallback(
            "cobblecompanion.msg.contentobserver_group_counts", "%s counter(s), %s subtractor(s)",
            String.valueOf(counterCount), String.valueOf(subtractorCount)), rightColX, y, labelColor);

        // Netzwerk-Info-Panel
        y = networkPanelTop;
        graphics.drawString(this.font, Component.translatable("cobblecompanion.gui.contentobserver.network_panel_title"), rightColX, y, 0xFFFFFF);
        y += NETWORK_PANEL_LINE_GAP_Y;
        Component status = networkConnected
            ? Component.translatableWithFallback("cobblecompanion.gui.contentobserver.network_connected_named", "Connected: %s", networkListName)
            : Component.translatable("cobblecompanion.gui.contentobserver.network_not_connected");
        graphics.drawString(this.font, status, rightColX, y, networkConnected ? 0x55FF55 : 0x808080);
    }

    @Override
    protected boolean mouseScrolledScaled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (grouped()) {
            if (isOverListRows(mouseX, mouseY, true, counterPanelTop)) {
                adjustScroll(true, scrollY);
                return true;
            }
            if (isOverListRows(mouseX, mouseY, false, subtractorPanelTop)) {
                adjustScroll(false, scrollY);
                return true;
            }
        }
        return super.mouseScrolledScaled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isOverListRows(double mouseX, double mouseY, boolean forCounter, int panelTop) {
        int rowsTop = panelTop + LIST_HEADER_H;
        int rowsBottom = rowsTop + visibleRows(forCounter) * LIST_ROW_H;
        return mouseX >= leftColX && mouseX < leftColX + LEFT_COL_W && mouseY >= rowsTop && mouseY < rowsBottom;
    }

    private void adjustScroll(boolean forCounter, double scrollY) {
        int maxScroll = Math.max(0, filteredEntries(forCounter).size() - visibleRows(forCounter));
        int offset = getScrollOffset(forCounter) - (int) Math.signum(scrollY);
        setScrollOffset(forCounter, Math.max(0, Math.min(offset, maxScroll)));
    }

    @Override
    protected boolean mouseClickedScaled(double mouseX, double mouseY, int button) {
        if (itemIdBox.isFocused()) {
            List<String> suggestions = itemIdSuggestions();
            int x = suggestX(itemIdBox);
            int sy = itemIdBox.getY();
            if (!suggestions.isEmpty() && mouseX >= x && mouseX < x + SUGGEST_BOX_W && mouseY >= sy && mouseY < sy + suggestions.size() * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - sy) / SUGGEST_ROW_H);
                if (index >= 0 && index < suggestions.size()) {
                    itemIdBox.setValue(suggestions.get(index));
                    itemIdBox.setCursorPosition(suggestions.get(index).length());
                    updateNetworkPriceFieldsFor(suggestions.get(index));
                    return true;
                }
            }
        }
        if (targetPlayerBox.isFocused()) {
            List<String> suggestions = playerSuggestions();
            int x = suggestX(targetPlayerBox);
            int sy = targetPlayerBox.getY();
            if (!suggestions.isEmpty() && mouseX >= x && mouseX < x + SUGGEST_BOX_W && mouseY >= sy && mouseY < sy + suggestions.size() * SUGGEST_ROW_H) {
                int index = (int) ((mouseY - sy) / SUGGEST_ROW_H);
                if (index >= 0 && index < suggestions.size()) {
                    targetPlayerBox.setValue(suggestions.get(index));
                    targetPlayerBox.setCursorPosition(suggestions.get(index).length());
                    return true;
                }
            }
        }

        // Rechtsklick auf die Verfalls-Stufe = eine Stufe runter (Linksklick = hoch, siehe Button-Callback).
        if (button == 1 && expiryButton.active && mouseX >= expiryButton.getX() && mouseX < expiryButton.getX() + expiryButton.getWidth()
                && mouseY >= expiryButton.getY() && mouseY < expiryButton.getY() + expiryButton.getHeight()) {
            cycleExpiry(-1);
            return true;
        }

        boolean grouped = grouped();
        if (grouped) {
            if (hitFormCheckbox(mouseX, mouseY, formInCounterCheckboxX, formInCounterCheckboxY)) {
                formInCounterList = !formInCounterList;
                return true;
            }
            if (hitFormCheckbox(mouseX, mouseY, formInSubtractorCheckboxX, formInSubtractorCheckboxY)) {
                formInSubtractorList = !formInSubtractorList;
                return true;
            }
            if (hitFormCheckbox(mouseX, mouseY, formBulkOnCheckboxX, formBulkOnCheckboxY)) {
                formBulkOn = !formBulkOn;
                if (formBulkOn) formBulkOff = false;
                return true;
            }
            if (hitFormCheckbox(mouseX, mouseY, formBulkOffCheckboxX, formBulkOffCheckboxY)) {
                formBulkOff = !formBulkOff;
                if (formBulkOff) formBulkOn = false;
                return true;
            }

            if (handleListClick(mouseX, mouseY, true, counterPanelTop)) return true;
            if (handleListClick(mouseX, mouseY, false, subtractorPanelTop)) return true;
        } else if (mouseX >= leftColX && mouseX < leftColX + LEFT_COL_W && mouseY >= listTop && mouseY < listBottom) {
            int index = (int) ((mouseY - listTop) / LIST_ROW_H);
            if (index >= 0 && index < entries.size()) {
                EditableEntry entry = entries.get(index);
                if (mouseX >= leftColX + LEFT_COL_W - LIST_REMOVE_COL_W) removeEntry(entry);
                else loadIntoForm(entry);
                return true;
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

    /** Klick-Handling für eine der beiden gruppierten Listen (Checkbox/Text/x/Scrollbar) - siehe renderListPanel für das Gegenstück beim Rendern. */
    private boolean handleListClick(double mouseX, double mouseY, boolean forCounter, int panelTop) {
        List<EditableEntry> filtered = filteredEntries(forCounter);
        int visibleRows = visibleRows(forCounter);
        int rowsTop = panelTop + LIST_HEADER_H;
        int rowsBottom = rowsTop + visibleRows * LIST_ROW_H;
        if (mouseX < leftColX || mouseX >= leftColX + LEFT_COL_W || mouseY < rowsTop || mouseY >= rowsBottom) return false;

        int trackX = leftColX + LEFT_COL_W - LIST_SCROLLBAR_W;
        if (filtered.size() > visibleRows && mouseX >= trackX) {
            int maxScroll = Math.max(0, filtered.size() - visibleRows);
            double ratio = (mouseY - rowsTop) / (double) (rowsBottom - rowsTop);
            setScrollOffset(forCounter, (int) Math.round(ratio * maxScroll));
            return true;
        }

        int offset = getScrollOffset(forCounter);
        int i = (int) ((mouseY - rowsTop) / LIST_ROW_H);
        int idx = offset + i;
        if (idx < 0 || idx >= filtered.size()) return false;
        EditableEntry entry = filtered.get(idx);

        if (mouseX >= leftColX + LEFT_COL_W - LIST_SCROLLBAR_W - LIST_REMOVE_COL_W) {
            removeEntry(entry);
        } else if (mouseX < leftColX + LIST_CHECKBOX_W) {
            // Nutzer-Vorgabe (geteilte Liste): Checkbox schaltet NUR für diesen Block und NUR für
            // diese eine Liste um, ob er das Katalog-Item erkennen soll.
            if (forCounter) entry.enabledAsCounter = !entry.enabledAsCounter;
            else entry.enabledAsSubtractor = !entry.enabledAsSubtractor;
        } else {
            loadIntoForm(entry);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
