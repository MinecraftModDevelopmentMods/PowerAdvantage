package com.mcmoddev.poweradvantage3.integration.mcmp;

import net.minecraftforge.fml.common.Loader;

public class IntegrationMCMP {
    public static boolean isLoaded() {
        return Loader.isModLoaded("mcmultipart");
    }
}