package com.mcmoddev.poweradvantage3.init;

import com.mcmoddev.poweradvantage3.block.BlockScaffolding;
import net.minecraft.block.Block;

public class ModBlocks {
    public static Block SCAFFOLDING;
    public static Block TUBE;

    public static void register() {
        SCAFFOLDING = new BlockScaffolding();
    }
}