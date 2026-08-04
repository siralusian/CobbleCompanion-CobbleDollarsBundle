# CobbleCompanion: CobbleDollars-Bundle

[🇩🇪 Deutsche Version weiter unten](#deutsch)

## English

**CobbleCompanion: CobbleDollars-Bundle** bundles CobbleCompanion's base features together with
the full CobbleDollars economy suite (Wallet, Create integration, CustomNPCs integration) — all in
a single jar. It's part of the modular **CobbleCompanion** family for Cobblemon — usable in both
singleplayer and multiplayer. Some features (the Professor tab, the friends system, the whole
economy) really come into their own on a server with multiple players; the base features work
just as well solo.

**Included in this file:**
- CobbleCompanion (Base)
- CobbleCompanion: CobbleDollars
- CobbleCompanion: CobbleDollars/Create
- CobbleCompanion: CobbleDollars/CustomNPCs

The leaner alternative to AllInOne for anyone who only needs the CobbleDollars economy — no
Pokémon pasture builder (CobblemonWorker), no Create/Let's Do bridge. Every integration activates
automatically once its matching foreign mod (Create, CobbleDollars, CustomNPCs) is installed — if
one is missing, only that specific feature stays inactive, the rest works normally.

### Opening the companion window

There's no dedicated keybind or item — CobbleCompanion replaces Cobblemon's own **Pokédex screen**
the moment you open it (via Cobblemon's usual keybind/item). If you're in spectator mode (where
Cobblemon's Pokédex can't be opened at all), use `/companion gamemode survival` first to switch
out.

### Base features

- **Pokédex** – Cobblemon's own Pokédex, embedded right inside the companion window.
- **Living Dex** – Tracks one caught specimen of each species from your PC boxes; optional "Living
  Dex+" also tracks shinies and regional/cosmetic forms as their own categories.
- **ToDo** (`/companion todo` also works from chat) – Every party/PC Pokémon ready to evolve, with
  a one-click Evolve button, plus a Dex-completion helper.
- **Who Needs?** (`/companion whoneeds <pokemon>`) – See your own spare copies of a species and
  which friends still need it.
- **Types** (`/companion type <query>`) – Type-effectiveness lookup cross-referenced against your
  own team/PC.
- **Team Builder** – Suggests up to 6 of your own Pokémon for a battle team, with reasoning per
  suggestion.
- **Search** – Universal quick-search across Pokémon, types, and friends.
- **Home** – Dashboard with progress bars and notification badges.
- **Friends** – see [Friends](#friends) below.
- **Professor** *(admin-only)* – see [Admin access](#admin-access--professor-tab) below.
- **Settings** – Personal preferences plus the [PC sorting helper](#pc-sorting-helper); operators
  additionally get server economy/gamemode rules and a large set of `/companion admin ...`
  sub-commands (full list below).

### Admin access / Professor tab

The Professor tab lets an admin inspect and edit **another player's** Pokédex, Living Dex, and
PC/team — mainly a multiplayer-server tool. Completely independent from vanilla OP status:

- `/companion op <name>` — grants **read-only** Professor tab access.
- `/companion adminop <name>` — grants **full** access (read + edit + can grant access to others).
- `/companion deop <name>` — revokes Professor access entirely (both Op and AdminOp).
- `/companion deadminop <name>` — downgrades AdminOp back down to read-only Op.
- **First admin on a fresh server**: run `/companion adminop <yourName>` once from the **server
  console** (not in-game chat) — the console can always grant access regardless of OP status.
- **Important:** vanilla server-operator status does **not** automatically grant Professor access
  — it's a fully separate permission system.

### Friends

- **Send a request**: Companion window → Friends tab → type a name (autocomplete) → "Add".
- **Accept/decline**: buttons appear next to incoming requests in the same tab.
- **Chat-only fallback** (for players without the client-side mod installed):
  `/companion accept friendrequest <name>` and `/companion accept gift <name>`.

### PC sorting helper

Enable in Settings → PC → "Sorting help". Once on, opening your **real Cobblemon PC** shows an
overlay: holding a Pokémon shows its target box/slot (green border = correct box open); holding
nothing marks every misplaced Pokémon (no border = correct, blue = needs to evolve first, red =
wrong slot, yellow = in a non-sorted box with a free target, orange = target team slot taken).
"Auto-name boxes" button in the same section.

### CobbleDollars (Wallet)

*Active once the CobbleDollars mod is installed.* Adds a **Wallet tab**: balance, transfers to
other players, transaction log, and Creative-time purchase (if enabled server-side).

### CobbleDollars/Create

*Active once Create + CobbleDollars are both installed.* Most of the following needs AdminOp (or
real OP for the block-editor shortcuts) — it's built for whoever runs the server's shop system.

- **Stock ticker prices**: live price display synced to your balance. **Ctrl+right-click a stock
  ticker** (real OP) opens the price editor instead of Create's normal order menu.
- **Content Observer ("Schlauer Beobachter")**: automatic sale counting for items funneled past it.
  **Ctrl+right-click the block** (real OP) opens its settings.
- **Linking a CobbleMerchant to a stock ticker or payout chest** (AdminOp): Ctrl+right-click the
  merchant to start link mode, then Ctrl+right-click a stock ticker or a chest. Ctrl+right-click
  the same merchant again to cancel.

### CobbleDollars/CustomNPCs

*Active once CustomNPCs-Unofficial-NeoForge + CobbleDollars are both installed.*

- **Turn a CustomNPC into a CobbleMerchant-style shop**: **Alt+right-click** the NPC (AdminOp) to
  toggle.
- **Linking it to a stock ticker/payout chest**: identical Ctrl+right-click flow as CobbleMerchant
  above (AdminOp, a ticker link is mandatory before the NPC can sell anything).

### All chat commands

| Command | Purpose |
|---|---|
| `/companion` / `/companion help` | Help text |
| `/companion party` | Show your own team |
| `/companion dex <pokemon>` | Pokédex info for a species |
| `/companion todo` | "What can I do right now?" overview |
| `/companion whoneeds <pokemon>` | Who still needs this species |
| `/companion type <query>` | Type report for a type or species |
| `/companion accept friendrequest <name>` | Accept a pending friend request (chat fallback) |
| `/companion accept gift <name>` | Accept a pending Pokémon gift (chat fallback) |
| `/companion gamemode <survival\|creative\|spectator>` | Switch mode while purchased creative time is active |
| `/companion op <name>` | Grant read-only Professor access |
| `/companion adminop <name>` | Grant full Professor access |
| `/companion deop <name>` | Revoke Professor access entirely |
| `/companion deadminop <name>` | Downgrade AdminOp to read-only Op |
| `/companion admin gamemode set/remove/list <dimension> [<mode>]` | Force a gamemode per dimension (AdminOp) |
| `/companion admin creativedimensions whitelist\|blacklist add\|remove\|list <player>` | Per-player creative-time purchase permission (AdminOp) |
| `/companion admin commandwhitelist add\|remove\|list <pattern>` | Commands allowed under restricted creative (AdminOp) |
| `/companion admin onlinebonus set\|remove\|list <player> [amount]` | Fixed CobbleDollars bonus per online interval (AdminOp) |
| `/companion admin gamemodeinventory enable\|disable\|status\|migrate` | Toggle per-gamemode inventory separation (AdminOp) |
| `/companion admin gamemodeinventory reset <player>` | Move a player's stored gamemode inventories into their pickup queue (AdminOp) |

**Dependencies:** Cobblemon (required). Create, CobbleDollars, CustomNPCs, and
create_mobile_packages are optional.

**Note:** Don't install alongside the individual CobbleDollars sub-modules or AllInOne.

### Building

No foreign mod jars ship in `libs/` for licensing reasons — place them there yourself before
building. Since the bundle contains the full source itself, none of the own
`CobbleCompanion-*.jar` split files are needed.

**Required:**
- `Cobblemon-neoforge-*.jar`

**Optional (enables that feature):**
- `CobbleDollars-neoforge-*.jar`, `curios-neoforge-*.jar`
- `rctapi-neoforge-*.jar`, `rctmod-neoforge-*.jar`
- `create-*.jar`, `architectury-*.jar`, `ponder-neoforge-*.jar`, `create_factory_abstractions-*.jar`,
  `create_mobile_packages-*.jar`
- `CustomNPCs-Unofficial-NeoForge-*.jar`

### Other CobbleCompanion projects

- [CobbleCompanion](https://github.com/siralusian/CobbleCompanion) — just the base, without any
  of this bundle's extensions.
- [CobbleCompanion: AllInOne](https://github.com/siralusian/CobbleCompanion-AllInOne) — this same
  bundle plus CobblemonWorker (the pasture builder), still in one file.
- [CobbleCompanion: CobbleDollars](https://github.com/siralusian/CobbleCompanion-CobbleDollars),
  [CobbleCompanion: CobbleDollars/Create](https://github.com/siralusian/CobbleCompanion-CobbleDollars-Create),
  [CobbleCompanion: CobbleDollars/CustomNPCs](https://github.com/siralusian/CobbleCompanion-CobbleDollars-CustomNPCs) —
  the same features as in this bundle, but as separate, individually installable jars.
- [CobbleCompanion: CobblemonWorker](https://github.com/siralusian/CobbleCompanion-CobblemonWorker) —
  the pasture builder, **not** included in this bundle.
- [CobbleCompanion: Create/Let's Do](https://github.com/siralusian/CreateLetsDo) — the Create ↔
  Let's Do: Farm & Charm automation bridge, **not** included in this bundle.
- [CreativeMenu](https://github.com/siralusian/CreativeMenu) — unrelated standalone mod, makes the
  Creative inventory menu freely customizable.
- [CopycatSign](https://github.com/siralusian/CopycatSign) — unrelated standalone mod, a Copycat
  block that displays a custom picture.

---

## Deutsch

**CobbleCompanion: CobbleDollars-Bundle** bündelt die CobbleCompanion-Basisfunktionen zusammen mit
der kompletten CobbleDollars-Wirtschafts-Suite (Wallet, Create-Anbindung, CustomNPCs-Anbindung) —
alles in einer einzigen Jar-Datei. Es ist Teil der modularen **CobbleCompanion**-Familie für
Cobblemon – nutzbar im Singleplayer und auf Servern. Manche Features (Professor-Tab,
Freundessystem, die ganze Wirtschaft) entfalten ihren vollen Nutzen erst auf einem Server mit
mehreren Spielern; die Basis-Features funktionieren genauso gut solo.

**In dieser Datei enthalten:**
- CobbleCompanion (Basis)
- CobbleCompanion: CobbleDollars
- CobbleCompanion: CobbleDollars/Create
- CobbleCompanion: CobbleDollars/CustomNPCs

Die schlankere Alternative zu AllInOne für alle, die nur die CobbleDollars-Wirtschaft brauchen –
ganz ohne Pokémon-Weidenbau (CobblemonWorker) und ohne die Create/Let's-Do-Brücke. Jede Anbindung
aktiviert sich automatisch, sobald der jeweils benötigte Fremd-Mod (Create, CobbleDollars,
CustomNPCs) installiert ist – fehlt einer, bleibt nur das zugehörige Feature inaktiv, der Rest
läuft normal weiter.

### Companion-Fenster öffnen

Es gibt keinen eigenen Keybind und kein eigenes Item – CobbleCompanion ersetzt Cobblemons
**Pokédex-Fenster** in dem Moment, in dem du es öffnest (über Cobblemons üblichen Keybind/Item).
Im Zuschauer-Modus nutze vorher `/companion gamemode survival`, um herauszuwechseln.

### Basis-Features

- **Pokédex** – Cobblemons eigener Pokédex, eingebettet im Begleiter-Fenster.
- **Living Dex** – Verfolgt je ein gefangenes Exemplar pro Art in deiner PC-Box; optionale "Living
  Dex+"-Erweiterung trackt zusätzlich Shinys und regionale/kosmetische Formen als eigene
  Kategorien.
- **ToDo** (auch per Chat `/companion todo`) – Alle entwicklungsbereiten Pokémon in Team und PC,
  mit Ein-Klick-Entwicklung, plus ein Dex-Fortschritts-Helfer.
- **Who Needs?** (`/companion whoneeds <pokemon>`) – Eigene übrige Exemplare und welche Freunde
  eine Art noch brauchen.
- **Types** (`/companion type <query>`) – Typen-Effektivitäts-Nachschlagewerk, abgeglichen mit
  deinem Team/PC.
- **Team Builder** – Schlägt bis zu 6 eigene Pokémon fürs Team vor, mit Begründung pro Vorschlag.
- **Search** – Universelle Schnellsuche über Pokémon, Typen und Freunde.
- **Home** – Startbildschirm mit Fortschrittsbalken und Hinweis-Badges.
- **Friends** – siehe [Freunde](#freunde) unten.
- **Professor** *(nur für Admins)* – siehe [Admin-Zugang](#admin-zugang--professor-tab) unten.
- **Settings** – Persönliche Einstellungen plus der [PC-Sortier-Helfer](#pc-sortier-helfer); für
  Operatoren zusätzlich Server-Wirtschafts-/Spielmodus-Regeln und eine Reihe
  `/companion admin ...`-Unterbefehle (vollständige Liste unten).

### Admin-Zugang / Professor-Tab

Der Professor-Tab lässt einen Admin den Pokédex, Living Dex sowie PC/Team **eines anderen
Spielers** einsehen und bearbeiten — vor allem ein Multiplayer-Server-Werkzeug. Komplett
unabhängig vom Vanilla-OP-Status:

- `/companion op <Name>` — gibt **nur Lesezugriff** auf den Professor-Tab.
- `/companion adminop <Name>` — gibt **vollen** Zugriff (lesen + bearbeiten + kann selbst weitere
  Spieler berechtigen).
- `/companion deop <Name>` — entzieht den Professor-Zugriff komplett (Op UND AdminOp).
- `/companion deadminop <Name>` — stuft von AdminOp auf reinen Lesezugriff (Op) herab.
- **Allererster Admin auf einem frischen Server**: einmalig `/companion adminop <DeinName>` über
  die **Server-Konsole** ausführen (nicht im Spiel-Chat) — die Konsole darf immer berechtigen.
- **Wichtig:** Vanilla-Server-Operator-Status gibt **keinen** automatischen Professor-Zugriff.

### Freunde

- **Anfrage senden**: Companion-Fenster → Freunde-Tab → Namen eintippen (Autovervollständigung) →
  "Hinzufügen".
- **Annehmen/Ablehnen**: Buttons erscheinen direkt bei eingehenden Anfragen im selben Tab.
- **Chat-Fallback**: `/companion accept friendrequest <Name>` und `/companion accept gift <Name>`.

### PC-Sortier-Helfer

Aktivieren unter Settings → PC → "Sortierhilfe". Danach zeigt das Öffnen deines echten Cobblemon-
PCs ein Overlay: Hältst du ein Pokémon, zeigt eine Infobox Ziel-Box/-Slot (grüner Rahmen = richtige
Box offen); hältst du nichts, wird jedes falsch platzierte Pokémon markiert (kein Rahmen =
richtig, Blau = muss erst entwickelt werden, Rot = falscher Slot, Gelb = liegt in
Nicht-Sortier-Box mit freiem Zielslot, Orange = Ziel-Team-Slot belegt). "Boxen automatisch
benennen"-Button im selben Bereich.

### CobbleDollars (Wallet)

*Aktiv, sobald die CobbleDollars-Mod installiert ist.* Fügt einen **Wallet-Tab** hinzu:
Kontostand, Überweisungen, Transaktions-Log und Creative-Zeitkauf (falls serverseitig aktiviert).

### CobbleDollars/Create

*Aktiv, sobald Create UND CobbleDollars installiert sind.* Das meiste braucht AdminOp (oder echten
OP für die Block-Editor-Abkürzungen) – gebaut für die Person, die das Server-Shop-System betreibt.

- **Lagerticker-Preise**: **Strg+Rechtsklick auf den Lagerticker** (echter OP) öffnet den
  Preis-Editor.
- **Schlauer Beobachter**: **Strg+Rechtsklick auf den Block** (echter OP) öffnet die
  Einstellungen.
- **CobbleMerchant verknüpfen** (AdminOp): Strg+Rechtsklick auf den Merchant startet den Modus,
  dann Strg+Rechtsklick auf Ticker oder Kiste. Erneutes Strg+Rechtsklick auf denselben Merchant
  bricht ab.

### CobbleDollars/CustomNPCs

*Aktiv, sobald CustomNPCs-Unofficial-NeoForge UND CobbleDollars installiert sind.*

- **CustomNPC zu einem Shop machen**: **Alt+Rechtsklick** (AdminOp) schaltet um.
- **Verknüpfen**: identischer Strg+Rechtsklick-Ablauf wie beim CobbleMerchant (AdminOp, eine
  Ticker-Verknüpfung ist Pflicht).

### Alle Chat-Befehle

| Befehl | Zweck |
|---|---|
| `/companion` / `/companion help` | Hilfetext |
| `/companion party` | Eigenes Team anzeigen |
| `/companion dex <pokemon>` | Pokédex-Info zu einer Art |
| `/companion todo` | „Was kann ich gerade tun?"-Übersicht |
| `/companion whoneeds <pokemon>` | Wer braucht diese Art noch |
| `/companion type <query>` | Typ-Report zu Typ oder Pokémon-Name |
| `/companion accept friendrequest <Name>` | Offene Freundschaftsanfrage annehmen (Chat-Fallback) |
| `/companion accept gift <Name>` | Offenes Pokémon-Geschenk annehmen (Chat-Fallback) |
| `/companion gamemode <survival\|creative\|spectator>` | Modus wechseln, solange gekaufte Creative-Zeit läuft |
| `/companion op <Name>` | Nur-Lese-Professor-Zugriff gewähren |
| `/companion adminop <Name>` | Vollen Professor-Zugriff gewähren |
| `/companion deop <Name>` | Professor-Zugriff komplett entziehen |
| `/companion deadminop <Name>` | Von AdminOp auf Nur-Lese-Zugriff herabstufen |
| `/companion admin gamemode set/remove/list <dimension> [<mode>]` | Erzwungenen Spielmodus pro Dimension setzen (AdminOp) |
| `/companion admin creativedimensions whitelist\|blacklist add\|remove\|list <player>` | Kaufberechtigung für Creative-Zeit pro Spieler (AdminOp) |
| `/companion admin commandwhitelist add\|remove\|list <pattern>` | Befehle für eingeschränktes Creative freischalten (AdminOp) |
| `/companion admin onlinebonus set\|remove\|list <player> [amount]` | Fixer CobbleDollars-Bonus pro Online-Intervall (AdminOp) |
| `/companion admin gamemodeinventory enable\|disable\|status\|migrate` | Inventar-Trennung nach Spielmodus global schalten (AdminOp) |
| `/companion admin gamemodeinventory reset <player>` | Gespeicherte Gamemode-Inventare eines Spielers in die Abholung verschieben (AdminOp) |

**Abhängigkeiten:** Cobblemon (erforderlich). Create, CobbleDollars, CustomNPCs und
create_mobile_packages sind optional.

**Hinweis:** Nicht gemeinsam mit den einzelnen CobbleDollars-Teilmodulen oder mit AllInOne
installieren.

### Bauen

Aus Lizenzgründen liegen keine fremden Mod-Jars in `libs/` im Repo – du musst sie vor dem Bauen
selbst dort ablegen. Da das Bundle den kompletten Quellcode selbst enthält, wird keine der
eigenen `CobbleCompanion-*.jar`-Split-Dateien benötigt.

**Erforderlich:**
- `Cobblemon-neoforge-*.jar`

**Optional (aktiviert das jeweilige Feature):**
- `CobbleDollars-neoforge-*.jar`, `curios-neoforge-*.jar`
- `rctapi-neoforge-*.jar`, `rctmod-neoforge-*.jar`
- `create-*.jar`, `architectury-*.jar`, `ponder-neoforge-*.jar`, `create_factory_abstractions-*.jar`,
  `create_mobile_packages-*.jar`
- `CustomNPCs-Unofficial-NeoForge-*.jar`

### Weitere CobbleCompanion-Projekte

- [CobbleCompanion](https://github.com/siralusian/CobbleCompanion) — nur die Basis, ohne die in
  diesem Bundle enthaltenen Erweiterungen.
- [CobbleCompanion: AllInOne](https://github.com/siralusian/CobbleCompanion-AllInOne) — dasselbe
  Bundle plus CobblemonWorker (Weidenbau), weiterhin in einer Datei.
- [CobbleCompanion: CobbleDollars](https://github.com/siralusian/CobbleCompanion-CobbleDollars),
  [CobbleCompanion: CobbleDollars/Create](https://github.com/siralusian/CobbleCompanion-CobbleDollars-Create),
  [CobbleCompanion: CobbleDollars/CustomNPCs](https://github.com/siralusian/CobbleCompanion-CobbleDollars-CustomNPCs) —
  dieselben Features wie in diesem Bundle, aber als separate, einzeln installierbare Dateien.
- [CobbleCompanion: CobblemonWorker](https://github.com/siralusian/CobbleCompanion-CobblemonWorker) —
  der Weidenbau, **nicht** in diesem Bundle enthalten.
- [CobbleCompanion: Create/Let's Do](https://github.com/siralusian/CreateLetsDo) — die Create ↔
  Let's Do: Farm & Charm-Brücke, **nicht** in diesem Bundle enthalten.
- [CreativeMenu](https://github.com/siralusian/CreativeMenu) — eigenständige, unabhängige Mod,
  macht das Creative-Menü frei gestaltbar.
- [CopycatSign](https://github.com/siralusian/CopycatSign) — eigenständige, unabhängige Mod, ein
  Copycat-Block, der ein frei wählbares Bild anzeigt.
