package dev.poligorn.aerocouriers.economy;

import net.minecraft.resources.ResourceLocation;

/** Numismatics-aligned spur denominations (largest first for greedy breakdown). */
public enum SpurDenomination {
    SUN(4096, "sun"),
    CROWN(512, "crown"),
    COG(64, "cog"),
    SPROCKET(16, "sprocket"),
    BEVEL(8, "bevel"),
    SPUR(1, "spur");

    public static final SpurDenomination[] HIGH_TO_LOW = values();

    private final int value;
    private final String id;
    private final ResourceLocation texture;

    SpurDenomination(int value, String id) {
        this.value = value;
        this.id = id;
        this.texture = ResourceLocation.fromNamespaceAndPath("aerocouriers", "textures/gui/coins/" + id + ".png");
    }

    public int value() {
        return value;
    }

    public String coinId() {
        return id;
    }

    public ResourceLocation texture() {
        return texture;
    }

    public String nameTranslationKey() {
        return "item.numismatics." + id;
    }

    public String englishNameTranslationKey() {
        return "ui.aerocouriers.coin." + id + ".en";
    }
}
