package com.mcmoddev.poweradvantage.misc;

import com.google.common.collect.Lists;
import net.minecraft.util.text.translation.I18n;

import java.awt.*;

public enum Crystal {
    RUBY("Ruby", new Color(0xE73D48).getRGB()),
    SAPPHIRE("Sapphire", new Color(0x5C75DB).getRGB())
    ;

    private String oreDict;

    private int rgb;

    Crystal(String oreDict, int rgb) {
        this.oreDict = oreDict;
        this.rgb = rgb;
    }

    public static Crystal getMaterial(int i) {
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
        return I18n.translateToLocal("material.crystal.poweradvantage." + getOreDict().toLowerCase() + ".name");
    }
}