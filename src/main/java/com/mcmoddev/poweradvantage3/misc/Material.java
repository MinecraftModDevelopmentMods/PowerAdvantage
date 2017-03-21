package com.mcmoddev.poweradvantage3.misc;

import com.google.common.collect.Lists;
import net.minecraft.util.text.translation.I18n;

import java.awt.*;

public enum Material {
    IRON("Iron", new Color(0xABABAB).getRGB(), MaterialType.ALL),
    GOLD("Gold", new Color(0xE2D145).getRGB(), MaterialType.ALL),
    COPPER("Copper", new Color(0xFF885A).getRGB(), MaterialType.ALL),
    SILVER("silver", new Color(0xD1E2E8).getRGB(), MaterialType.ALL),
    ELECTRUM("Electrum", new Color(0xABA431).getRGB(), MaterialType.CRAFTING),
    LEAD("Lead", new Color(0x3B3E4E).getRGB(), MaterialType.ALL),
    TIN("Tin", new Color(0xDEDACD).getRGB(), MaterialType.ALL),
    SIGNALUM("Signalum", new Color(0xCB3C18).getRGB(), MaterialType.CRAFTING),
    BRONZE("Bronze", new Color(0xC87D29).getRGB(), MaterialType.CRAFTING),
    IRIDIUM("Iridium", new Color(0xFFFFFF).getRGB(), MaterialType.ALL),;

    private String oreDict;
    private java.util.List<MaterialType> materialTypes;

    private int rgb;

    Material(String oreDict, int rgb, MaterialType... types) {
        this.oreDict = oreDict;
        this.rgb = rgb;
        this.materialTypes = Lists.newArrayList(types);
    }

    public static Material getMaterial(int i) {
        if (i >= values().length)
            i = 0;
        return values()[i];
    }

    public String getOreDict() {
        return oreDict;
    }

    public int getRGB() {
        return rgb;
    }

    public String translateServer() {
        return I18n.translateToLocal("material.poweradvantage3." + getOreDict().toLowerCase() + ".name");
    }

    public boolean canBe(MaterialType type) {
        return this.materialTypes.contains(type);
    }
}