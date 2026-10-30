package zone.moddev.mc.advantageworks;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

final class NativeStacks {
    static int count(ItemStack stack) { return stack == null ? 0 : stack.stackSize; }
    static NBTTagCompound tag(ItemStack stack) {
        if ((stack == null || stack.stackSize <= 0)) throw new IllegalArgumentException("Cannot tag an empty fixture item");
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        return stack.getTagCompound();
    }
}
