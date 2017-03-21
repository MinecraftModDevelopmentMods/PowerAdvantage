package com.mcmoddev.poweradvantage3;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class EventHandler {
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if(!player.getActivePotionEffects().isEmpty()) {
            boolean alert = false;
            for(PotionEffect potionEffect : player.getActivePotionEffects()) {
                if(potionEffect.getDuration()<=200)
                    alert=true;
            }

        }
    }
}
