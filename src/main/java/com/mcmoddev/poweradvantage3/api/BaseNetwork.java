package com.mcmoddev.poweradvantage3.api;

import com.google.common.collect.Sets;
import net.minecraft.world.World;

import java.util.LinkedHashSet;

public class BaseNetwork<ACC, NET extends BaseNetwork<ACC,NET>> {
    public LinkedHashSet<ITileTransmitter<ACC, NET>> transmitters = Sets.newLinkedHashSet();

    protected World worldObj = null;


}
