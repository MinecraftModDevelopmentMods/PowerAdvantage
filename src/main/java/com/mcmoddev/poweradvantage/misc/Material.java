package com.mcmoddev.poweradvantage.misc;

import net.minecraft.util.text.translation.I18n;

import java.awt.*;

public enum Material {
    IRON("iron", new Color(0xABABAB).getRGB()),
    GOLD("gold", new Color(0xE2D145).getRGB()),
    COPPER("copper", new Color(0xFF885A).getRGB()),
    SILVER("silver", new Color(0xD1E2E8).getRGB()),
    ELECTRUM("electrum", new Color(0xABA431).getRGB()),
    LEAD("lead", new Color(0x3B3E4E).getRGB()),
    TIN("tin", new Color(0xDEDACD).getRGB()),;

    private String oreDict;

    private int rgb;

    Material(String oreDict, int rgb) {
        this.oreDict = oreDict;
        this.rgb = rgb;
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
        return I18n.translateToLocal("material.poweradvantage." + getOreDict() + ".name");
    }
}