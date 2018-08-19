package cyano.poweradvantage.api.example;

import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.api.simple.BlockSimplePowerConduit;

public class RedstonePowerConductorBlock extends BlockSimplePowerConduit{

	
	 public RedstonePowerConductorBlock() {
		super(Material.piston, 0.5f, 0.20f, new ConduitType("redstone"));
		super.setCreativeTab(CreativeTabs.tabMisc);
	}

	@Override
	    public void onBlockClicked(World w, BlockPos p, EntityPlayer player){
	    	if(w.isRemote)return;
	    	PoweredEntity e = (PoweredEntity)w.getTileEntity(p);
	    	player.addChatMessage(new net.minecraft.util.ChatComponentText(e.getType()+": "+e.getEnergy()));
	    }

	
	@SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick(final World world, final BlockPos coord, final IBlockState bs, final Random prng) {
        TileEntity e = world.getTileEntity(coord);
        if(e != null && e instanceof PoweredEntity){
        	if(((PoweredEntity)e).getEnergy() > 0){
        		final double x = coord.getX() + 0.5 + (prng.nextFloat() - 0.5) * 0.2;
                final double y = coord.getY() + 0.5 + (prng.nextFloat() - 0.5) * 0.2;
                final double z = coord.getZ() + 0.5 + (prng.nextFloat() - 0.5) * 0.2;
                world.spawnParticle(EnumParticleTypes.REDSTONE, x, y, z, 1f, 1f, 1f, new int[0]);
        	}
        }
    }
}