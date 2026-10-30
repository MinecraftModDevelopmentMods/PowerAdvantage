package zone.moddev.mc.advantageworks;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

final class NativeStacks {
    static NBTTagCompound tag(ItemStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Cannot tag an empty fixture item");
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        return stack.getTagCompound();
    }
}
