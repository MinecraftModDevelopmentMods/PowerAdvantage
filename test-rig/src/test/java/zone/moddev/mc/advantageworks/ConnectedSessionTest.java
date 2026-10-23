package zone.moddev.mc.advantageworks;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import zone.moddev.mc.advantageworks.connected.ConnectedSession;

public class ConnectedSessionTest {
    private JsonObject definition(String blocks) {
        return new Gson().fromJson("{format:1,bounds:[0,78,0,280,84,64],blocks:[" + blocks + "]}", JsonObject.class);
    }

    @Test public void acceptsBoundedFinitePlacement() {
        ConnectedSession.validate(definition("{id:'source',x:1,y:80,z:1,name:'minecraft:chest'}"));
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsInfinitePower() {
        ConnectedSession.validate(definition("{id:'source',x:1,y:80,z:1,name:'poweradvantage:infinite_steam'}"));
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsOverlappingPlacements() {
        ConnectedSession.validate(definition("{id:'one',x:1,y:80,z:1,name:'minecraft:chest'},"
                + "{id:'two',x:1,y:80,z:1,name:'minecraft:chest'}"));
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsOutsideBounds() {
        ConnectedSession.validate(definition("{id:'source',x:281,y:80,z:1,name:'minecraft:chest'}"));
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsUnsafeIdentity() {
        ConnectedSession.validate(definition("{id:'../source',x:1,y:80,z:1,name:'minecraft:chest'}"));
    }
}
