package cyano.poweradvantage.api.fluid;

import com.mcmoddev.basemetals.init.Fluids;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Created by Jacob on 3/8/2018.
 */
@Mod.EventBusSubscriber(Side.CLIENT)
public class SwimEventHandler {

    @SubscribeEvent
    public void playerSwim(EntityViewRenderEvent.FogColors e)
    {
        System.out.println("Player swimming in thing");
        Entity ent = e.getEntity();
        World w = ent.getEntityWorld();
        BlockPos pos = ent.getPosition();
        IBlockState bs = w.getBlockState(pos);
        Block b = bs.getBlock();
        if(b.equals(cyano.poweradvantage.init.Fluids.crude_oil.getBlock()))
        {
            int red = 34, green = 11, blue = 11;
            e.setRed(red); e.setGreen(green); e.setBlue(blue);
        }
    }

    @SubscribeEvent
    public void playerSwim(EntityViewRenderEvent.FogDensity e)
    {
        e.setCanceled(true);
        Entity ent = e.getEntity();
        World w = ent.getEntityWorld();
        BlockPos pos = ent.getPosition();
        IBlockState bs = w.getBlockState(pos);
        Block b = bs.getBlock();
        if(b.equals(cyano.poweradvantage.init.Fluids.crude_oil.getBlock()))
        {
            float alpha = 234.0F;
            e.setDensity(alpha/100.0F);
        }
    }

}
