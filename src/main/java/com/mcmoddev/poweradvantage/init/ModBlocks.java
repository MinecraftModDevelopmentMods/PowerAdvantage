package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.poweradvantage.block.BlockScaffolding;
import net.minecraft.block.Block;

public class ModBlocks {
    public static Block SCAFFOLDING;
    public static Block TUBE;

    public static void register() {
        SCAFFOLDING = new BlockScaffolding();
    }
}