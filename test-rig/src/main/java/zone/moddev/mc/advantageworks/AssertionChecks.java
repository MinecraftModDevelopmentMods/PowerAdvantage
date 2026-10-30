package zone.moddev.mc.advantageworks;

import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;

/** Fail closed when a measurement is unavailable, malformed or not finite. */
final class AssertionChecks {
    private AssertionChecks() { }

    static String verdict(boolean matched, String knownBug, Exception error) {
        if (error != null) return "FAIL";
        if (knownBug != null && !knownBug.isEmpty()) return matched ? "UNEXPECTED_PASS" : "EXPECTED_FAIL";
        return matched ? "PASS" : "FAIL";
    }

    static String aggregate(List<Map<String, Object>> assertions, boolean manual) {
        boolean tested = false, expected = false, unexpected = false;
        for (Map<String, Object> assertion : assertions) {
            String status = String.valueOf(assertion.get("status"));
            if ("FAIL".equals(status)) return "FAIL";
            if ("EXPECTED_FAIL".equals(status)) expected = true;
            else if ("UNEXPECTED_PASS".equals(status)) unexpected = true;
            else if ("PASS".equals(status)) tested = true;
            else if (!"SKIP".equals(status)) return "FAIL";
        }
        if (unexpected) return "UNEXPECTED_PASS";
        if (expected) return "EXPECTED_FAIL";
        if (!tested) return "SKIP";
        return manual ? "MANUAL_REQUIRED" : "PASS";
    }

    static boolean compare(double actual, String operator, Number expected) {
        if (expected == null || !Double.isFinite(actual) || !Double.isFinite(expected.doubleValue())) return false;
        double value = expected.doubleValue();
        switch (operator == null ? "equals" : operator) {
            case "equals": return Math.abs(actual - value) < 0.0001;
            case "min": return actual >= value;
            case "max": return actual <= value;
            case "greater": return actual > value;
            case "less": return actual < value;
            default: throw new IllegalArgumentException("Unsupported comparison " + operator);
        }
    }

    static int fluidAmount(NBTTagCompound tile, String key, String expectedFluid) {
        if (!tile.contains(key, 10)) throw new IllegalArgumentException("Missing tank compound " + key);
        NBTTagCompound tank = tile.getCompound(key);
        // Forge writes an explicit empty marker; a missing tank is not an empty tank.
        if (tank.contains("Empty", 1) && tank.getBoolean("Empty")
                && !tank.keySet().contains("Amount") && !tank.keySet().contains("FluidName")) return 0;
        if (tank.keySet().contains("Empty")) throw new IllegalArgumentException("Contradictory empty tank marker at " + key);
        if (!tank.contains("Amount", 3) || !tank.contains("FluidName", 8)) {
            throw new IllegalArgumentException("Missing tank amount or fluid identity at " + key);
        }
        int amount = tank.getInt("Amount");
        if (amount < 0 || (amount > 0 && tank.getString("FluidName").isEmpty())) {
            throw new IllegalArgumentException("Invalid tank contents at " + key);
        }
        return expectedFluid == null || expectedFluid.equals(tank.getString("FluidName")) ? amount : 0;
    }
}
