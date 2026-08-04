# CobbleCompanion: CobbleDollars-Bundle

Teil der **CobbleCompanion**-Familie — ein modulares Baukasten-System für Cobblemon-Server.
Siehe [Verwandte Module](#verwandte-module--related-modules) unten für alle Varianten.

**Kurzbeschreibung:**
Basis + komplette CobbleDollars-Suite in einer Datei – ohne CobblemonWorker und ohne die
Create/Let's-Do-Brücke.

## Beschreibung

Die schlankere Alternative zu AllInOne für alle, die **nur die CobbleDollars-Wirtschaft**
brauchen: bündelt

- CobbleCompanion: Basis
- CobbleCompanion: CobbleDollars
- CobbleCompanion: CobbleDollars/Create
- CobbleCompanion: CobbleDollars/CustomNPCs

in **einer Datei** – ganz ohne den Pokémon-Weidenbau (CobblemonWorker) und ohne die
Create/Let's-Do-Brücke. Wie bei AllInOne sind alle Fremd-Mod-Anbindungen (Create, CobbleDollars,
CustomNPCs) optional und aktivieren sich automatisch.

**Abhängigkeiten:** Cobblemon (erforderlich). Create, CobbleDollars, CustomNPCs und
create_mobile_packages sind optional.

**Hinweis:** Nicht gemeinsam mit den einzelnen CobbleDollars-Teilmodulen oder mit AllInOne
installieren.

---

## English

**Summary:**
Basis + the full CobbleDollars suite in one file – no CobblemonWorker, no Create/Let's Do bridge.

## Description

The leaner alternative to AllInOne for anyone who only needs **the CobbleDollars economy**:
bundles

- CobbleCompanion: Basis
- CobbleCompanion: CobbleDollars
- CobbleCompanion: CobbleDollars/Create
- CobbleCompanion: CobbleDollars/CustomNPCs

into **a single file** — no Pokémon pasture builder (CobblemonWorker), no Create/Let's Do bridge.
Just like AllInOne, every foreign-mod integration (Create, CobbleDollars, CustomNPCs) is optional
and activates automatically.

**Dependencies:** Cobblemon (required). Create, CobbleDollars, CustomNPCs, and
create_mobile_packages are optional.

**Note:** Don't install alongside the individual CobbleDollars sub-modules or AllInOne.

---

## Bauen / Building

Aus Lizenzgründen liegen keine fremden Mod-Jars in `libs/` im Repo – du musst sie vor dem Bauen
selbst dort ablegen. Da das Bundle den kompletten Quellcode selbst enthält, wird **keine** der
eigenen `CobbleCompanion-*.jar`-Split-Dateien benötigt.
*No foreign mod jars ship in `libs/` for licensing reasons — place them there yourself before
building. Since the bundle contains the full source itself, none of the own `CobbleCompanion-*.jar`
split files are needed.*

**Erforderlich / Required:**
- `Cobblemon-neoforge-*.jar`

**Optional (aktiviert das jeweilige Feature) / Optional (enables that feature):**
- `CobbleDollars-neoforge-*.jar`, `curios-neoforge-*.jar`
- `rctapi-neoforge-*.jar`, `rctmod-neoforge-*.jar`
- `create-*.jar`, `architectury-*.jar`, `ponder-neoforge-*.jar`, `create_factory_abstractions-*.jar`,
  `create_mobile_packages-*.jar`
- `CustomNPCs-Unofficial-NeoForge-*.jar`

## Verwandte Module / Related modules

- [CobbleCompanion](https://github.com/siralusian/CobbleCompanion) — Basis
- [CobbleCompanion: CobbleDollars](https://github.com/siralusian/CobbleCompanion-CobbleDollars)
- [CobbleCompanion: CobbleDollars/Create](https://github.com/siralusian/CobbleCompanion-CobbleDollars-Create)
- [CobbleCompanion: CobbleDollars/CustomNPCs](https://github.com/siralusian/CobbleCompanion-CobbleDollars-CustomNPCs)
- [CobbleCompanion: CobblemonWorker](https://github.com/siralusian/CobbleCompanion-CobblemonWorker)
- [CobbleCompanion: Create/Let's Do](https://github.com/siralusian/CreateLetsDo)
- [CobbleCompanion: AllInOne](https://github.com/siralusian/CobbleCompanion-AllInOne)
