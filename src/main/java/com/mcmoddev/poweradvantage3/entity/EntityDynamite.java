package com.mcmoddev.poweradvantage3.entity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

public class EntityDynamite extends EntityThrowable {
    public EntityDynamite(World worldIn) {
        super(worldIn);
    }

    public EntityDynamite(World worldIn, double x, double y, double z) {
        super(worldIn, x, y, z);
    }

    public EntityDynamite(World worldIn, EntityLivingBase throwerIn) {
        super(worldIn, throwerIn);
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        world.createExplosion(this,
                result.getBlockPos().getX(),
                result.getBlockPos().getY(),
                result.getBlockPos().getZ(),
                2.0F,
                true
        );
        this.setDead();
    }
}
