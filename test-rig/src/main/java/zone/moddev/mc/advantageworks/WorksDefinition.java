package zone.moddev.mc.advantageworks;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

final class WorksDefinition {
    int format;
    String minecraft;
    List<District> districts = new ArrayList<>();
    List<Station> stations = new ArrayList<>();

    static final class District {
        String id;
        String name;
        int woolMeta;
    }

    static final class Station {
        String id;
        String name;
        String district;
        int[] origin;
        int[] boundsMin;
        int[] boundsMax;
        boolean exclusive;
        boolean enclosed;
        List<String> requiredMods = new ArrayList<>();
        List<Placement> placements = new ArrayList<>();
        List<Action> setupActions = new ArrayList<>();
        List<Action> startActions = new ArrayList<>();
        List<Action> stopActions = new ArrayList<>();
        List<Assertion> assertions = new ArrayList<>();
        List<String> manualChecks = new ArrayList<>();
    }

    static class Placement {
        int[] pos;
        String block;
        int meta;
        boolean optional;
        JsonObject nbt;
        List<String> requiredMods = new ArrayList<>();
    }

    static final class Action extends Placement {
        String type;
        String item;
        int slot;
        int count = 1;
        int damage;
        String entity;
        String fluid;
        String enchantment;
        int level;
        long value;
        int transferLimit;
        Long expectedValue;
    }

    static final class Assertion {
        String id;
        String type;
        int[] pos;
        List<int[]> positions = new ArrayList<>();
        String fluid;
        String block;
        String property;
        Integer meta;
        String item;
        Integer count;
        String nbtPath;
        String expectedText;
        String operator = "equals";
        Double value;
        Integer slot;
        Integer energyIndex;
        String expectedFailure;
        boolean optional;
        List<String> requiredMods = new ArrayList<>();
    }
}
