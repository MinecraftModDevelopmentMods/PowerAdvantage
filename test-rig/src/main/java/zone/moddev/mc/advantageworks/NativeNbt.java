package zone.moddev.mc.advantageworks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

final class NativeNbt {
    static NBTTagCompound decode(String encoded) {
        try {
            return CompressedStreamTools.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
        } catch (IOException failure) {
            throw new IllegalArgumentException("Cannot read native typed NBT", failure);
        }
    }

    static String encode(NBTTagCompound tag) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(tag, output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot record native typed NBT", failure);
        }
    }
}
