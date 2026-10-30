package zone.moddev.mc.poweradvantage.util;

import java.lang.reflect.Field;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.LockableTileEntity;
import net.minecraft.world.LockCode;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

/** Keeps automation from bypassing vanilla container locks. */
public final class InventoryLocks {
    // Forge 1.14 has no lock getter. Resolve its SRG field once, not once per transfer.
    private static final class Fields {
        static final Field CODE = ObfuscationReflectionHelper.findField(LockableTileEntity.class, "field_174901_a");
        static final Field FIRST = ObfuscationReflectionHelper.findField(
            net.minecraft.inventory.DoubleSidedInventory.class, "field_70477_b");
        static final Field SECOND = ObfuscationReflectionHelper.findField(
            net.minecraft.inventory.DoubleSidedInventory.class, "field_70478_c");
    }

    private InventoryLocks() {}

    public static boolean isLocked(Object inventory) {
        if (inventory instanceof InventoryWrapper) return ((InventoryWrapper) inventory).isLocked();
        try {
            if (inventory instanceof net.minecraft.inventory.DoubleSidedInventory)
                return isLocked(Fields.FIRST.get(inventory)) || isLocked(Fields.SECOND.get(inventory));
            return inventory instanceof LockableTileEntity
                    && !((LockCode) Fields.CODE.get(inventory)).func_219964_a(ItemStack.EMPTY);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot inspect vanilla inventory lock", failure);
        }
    }
}
