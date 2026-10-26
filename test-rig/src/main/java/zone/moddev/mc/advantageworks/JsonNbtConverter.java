package zone.moddev.mc.advantageworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ByteNBT;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.DoubleNBT;
import net.minecraft.nbt.IntNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.LongNBT;
import net.minecraft.nbt.StringNBT;

import java.util.Map;

final class JsonNbtConverter {
    private JsonNbtConverter() {
    }

    static CompoundNBT toCompound(JsonObject object) {
        CompoundNBT result = new CompoundNBT();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            result.put(entry.getKey(), toTag(entry.getValue()));
        }
        return result;
    }

    private static INBT toTag(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("NBT values may not be null");
        }
        if (element.isJsonObject()) return toCompound(element.getAsJsonObject());
        if (element.isJsonArray()) {
            ListNBT result = new ListNBT();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) result.add(toTag(child));
            return result;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return new ByteNBT((byte) (primitive.getAsBoolean() ? 1 : 0));
        if (primitive.isString()) return new StringNBT(primitive.getAsString());
        String number = primitive.getAsString();
        if (number.contains(".") || number.contains("e") || number.contains("E")) {
            return new DoubleNBT(primitive.getAsDouble());
        }
        long value = primitive.getAsLong();
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                ? new IntNBT((int) value) : new LongNBT(value);
    }
}
