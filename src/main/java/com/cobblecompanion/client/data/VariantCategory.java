package com.cobblecompanion.client.data;

/**
 * Die 7 unabhängig togglebaren Sammel-Kategorien von "Living Dex+" (siehe Umsetzungsplan). Die
 * ID ist stabil und wird in ClientSettingsHelper.livingDexPlusCategories persistiert - NIE
 * umnummerieren, nur anhängen.
 */
public enum VariantCategory {
    BASE_POKEDEX(0, "cobblecompanion.livingdexplus.cat.base_pokedex"),
    BASE_LIVING_DEX(1, "cobblecompanion.livingdexplus.cat.base_living_dex"),
    // War früher "Shiny" - tatsächlich aber Shiny PRO ART (Living-Dex-Granularität), deshalb
    // umbenannt zu "Shiny Living Dex" und um das Pendant SHINY_POKEDEX (Familien-Kollabierung
    // wie BASE_POKEDEX) ergänzt (Nutzer-Feedback: 29 Boxen entsprachen genau der Living-Dex-Zahl).
    BASE_SHINY(2, "cobblecompanion.livingdexplus.cat.shiny_living_dex"),
    REGIONAL_FORMS(3, "cobblecompanion.livingdexplus.cat.regional_forms"),
    REGIONAL_FORMS_SHINY(4, "cobblecompanion.livingdexplus.cat.regional_forms_shiny"),
    COSMETIC_FORMS(5, "cobblecompanion.livingdexplus.cat.cosmetic_forms"),
    COSMETIC_FORMS_SHINY(6, "cobblecompanion.livingdexplus.cat.cosmetic_forms_shiny"),
    SHINY_POKEDEX(7, "cobblecompanion.livingdexplus.cat.shiny_pokedex");

    public final int id;
    public final String labelKey;

    VariantCategory(int id, String labelKey) {
        this.id = id;
        this.labelKey = labelKey;
    }

    public static final VariantCategory[] ALL = values();

    public static VariantCategory byId(int id) {
        for (VariantCategory c : ALL) if (c.id == id) return c;
        return null;
    }
}
