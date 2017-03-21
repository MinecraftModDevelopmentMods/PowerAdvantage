package com.mcmoddev.poweradvantage3.misc;

public enum MaterialType {
    CASING,
    CRUSHED,
    DENSE_PLATE,
    PLATE,
    PURIFIED,
    SMALL_DUST;

    public static final MaterialType[] ALL = new MaterialType[]{
            CASING,
            CRUSHED,
            DENSE_PLATE,
            PLATE,
            PURIFIED,
            SMALL_DUST
    };

    public static final MaterialType[] CRAFTING = new MaterialType[]{
            CASING,
            DENSE_PLATE,
            PLATE,
    };
}
