package com.mcmoddev.poweradvantage3.init;

import com.mcmoddev.poweradvantage3.misc.Material;
import com.mcmoddev.poweradvantage3.misc.MaterialType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public class ModOreDicts {
    public static void register() {
        for (Material material : Material.values()) {
            if (material.canBe(MaterialType.CASING))
                OreDictionary.registerOre("casing" + material.getOreDict(), new ItemStack(ModItems.CASING));
            if (material.canBe(MaterialType.CRUSHED))
                OreDictionary.registerOre("crushed" + material.getOreDict(), new ItemStack(ModItems.CRUSHED));
            if (material.canBe(MaterialType.PURIFIED))
                OreDictionary.registerOre("crushedPurified" + material.getOreDict(), new ItemStack(ModItems.PURIFIED));
            if (material.canBe(MaterialType.PLATE))
                OreDictionary.registerOre("plate" + material.getOreDict(), new ItemStack(ModItems.PLATE));
            if (material.canBe(MaterialType.DENSE_PLATE))
                OreDictionary.registerOre("plateDense" + material.getOreDict(), new ItemStack(ModItems.DENSE_PLATE));
            if (material.canBe(MaterialType.SMALL_DUST))
                OreDictionary.registerOre("dustTiny" + material.getOreDict(), new ItemStack(ModItems.SMALLDUST));
        }
    }
}
