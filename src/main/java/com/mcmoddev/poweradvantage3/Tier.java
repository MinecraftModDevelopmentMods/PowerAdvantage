package com.mcmoddev.poweradvantage3;

import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.text.TextFormatting;

public class Tier {


    public enum BaseTier implements IStringSerializable {
        BASIC(EnumDyeColor.LIME, TextFormatting.GREEN);

        public EnumDyeColor dyeColor;
        public TextFormatting textFormatting;

        BaseTier(EnumDyeColor dyeColor, TextFormatting textFormatting) {
            this.dyeColor = dyeColor;
            this.textFormatting = textFormatting;
        }

        @Override
        public String getName() {
            return name().toLowerCase();
        }
    }

    public enum FluidPipeTier implements ITier {
        BASIC(1000,100)
        ;

        public int pipeCapacity;
        public int pipePullAmount;

        FluidPipeTier(int pipeCapacity, int pipePullAmount) {
            this.pipeCapacity = pipeCapacity;
            this.pipePullAmount = pipePullAmount;
        }

        @Override
        public BaseTier getBaseTier() {
            return BaseTier.values()[ordinal()];
        }
    }

    public interface ITier {
        BaseTier getBaseTier();
    }
}