package zone.moddev.mc.advantageworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.INBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;

import java.util.Map;

final class JsonNbtConverter {
    private JsonNbtConverter() {
    }

    static NBTTagCompound toCompound(JsonObject object) {
        NBTTagCompound result = new NBTTagCompound();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            result.setTag(entry.getKey(), toTag(entry.getValue()));
        }
        return result;
    }

    private static INBTBase toTag(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("NBT values may not be null");
        }
        if (element.isJsonObject()) return toCompound(element.getAsJsonObject());
        if (element.isJsonArray()) {
            NBTTagList result = new NBTTagList();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) result.add(toTag(child));
            return result;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return new NBTTagByte((byte) (primitive.getAsBoolean() ? 1 : 0));
        if (primitive.isString()) return new NBTTagString(primitive.getAsString());
        String number = primitive.getAsString();
        if (number.contains(".") || number.contains("e") || number.contains("E")) {
            return new NBTTagDouble(primitive.getAsDouble());
        }
        long value = primitive.getAsLong();
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                ? new NBTTagInt((int) value) : new NBTTagLong(value);
    }
}
