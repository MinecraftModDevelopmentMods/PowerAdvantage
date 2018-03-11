package cyano.poweradvantage.api.fluid;

import com.mcmoddev.basemetals.init.Fluids;
import cyano.poweradvantage.init.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Created by Jacob on 3/8/2018.
 */
@Mod.EventBusSubscriber
public class FluidEventHandler {

//    @SubscribeEvent
//    public static void playerSwim(EntityViewRenderEvent.FogColors e)
//    {
//
//        Entity ent = e.getEntity();
//        World w = ent.getEntityWorld();
//        BlockPos pos = ent.getPosition();
//        IBlockState bs = w.getBlockState(pos);
//        Block b = bs.getBlock();
//        BlockPos pos2 = new BlockPos(pos.getX(), ent.getEyeHeight(), pos.getY());
//		IBlockState bs2 = w.getBlockState(pos2);
//		Block b2 = bs2.getBlock();
//        System.out.println(String.format("Player swimming in %s or %s", b.getRegistryName().toString(), b2.getRegistryName().toString()));
//        Block crudeOil = cyano.poweradvantage.init.Fluids.crude_oil.getBlock();
//        if(b.equals(crudeOil) || b2.equals(crudeOil))
//        {
//            System.out.println("Player swimming in oil");
//            float red = 34.0f/256.0f, green = 11.0f/256.0f, blue = 11.0f/256.0f;
//            e.setRed(red); e.setGreen(green); e.setBlue(blue);
//            GlStateManager.setFog(GlStateManager.FogMode.EXP);
//    		GlStateManager.setFogDensity(2.0F);
//        }


    @SideOnly(Side.CLIENT)
    @SubscribeEvent(priority= EventPriority.NORMAL, receiveCanceled=true)
    public void onSwimEvent(EntityViewRenderEvent.FogDensity event)
    {
        Entity ent = event.getEntity();
        World w = ent.getEntityWorld();
        BlockPos pos = ent.getPosition();
        IBlockState bs = w.getBlockState(pos);
        Block b = bs.getBlock();
        BlockPos pos2 = new BlockPos(pos.getX(), ent.getEyeHeight(), pos.getY());
		IBlockState bs2 = w.getBlockState(pos2);
		Block b2 = bs2.getBlock();
//        System.out.println(String.format("Player swimming in %s or %s", b.getRegistryName().toString(), b2.getRegistryName().toString()));
        Block crudeOil = cyano.poweradvantage.init.Fluids.crude_oil.getBlock();
        Block refinedOil = cyano.poweradvantage.init.Fluids.refined_oil.getBlock();
        if(b.equals(crudeOil) || b2.equals(crudeOil))
        {
            event.setDensity(0.7F);
            event.setCanceled(true);
        } else if (b.equals(refinedOil) || b2.equals(refinedOil))
        {
            event.setDensity(0.5F);
            event.setCanceled(true);
        }
         // must cancel event for event handler to take effect
    }

//    @SubscribeEvent
//    public static void playerSwim(EntityViewRenderEvent.FogDensity e)
//    {
//        e.setCanceled(true);
//        Entity ent = e.getEntity();
//        World w = ent.getEntityWorld();
//        BlockPos pos = ent.getPosition();
//        IBlockState bs = w.getBlockState(pos);
//        Block b = bs.getBlock();
//        if(b.equals(cyano.poweradvantage.init.Fluids.crude_oil.getBlock()))
//        {
//            float alpha = 256.0F;
//            e.setDensity(100.0f/alpha);
//        }
//    }

}
