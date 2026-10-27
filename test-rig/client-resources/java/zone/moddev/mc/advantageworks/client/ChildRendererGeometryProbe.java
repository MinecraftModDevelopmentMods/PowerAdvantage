package zone.moddev.mc.advantageworks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.mojang.datafixers.util.Pair;
import cyano.poweradvantage.api.PoweredEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.entity.EntityType;
import net.minecraft.resources.IResource;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Additional CPU geometry evidence from the registered renderers; never opens a world. */
final class ChildRendererGeometryProbe {
    private static final float EPSILON = 0.0001F;
    private static final int LIGHT = 0xF000F0;
    private static final String STEAM_RENDERER = "zone.moddev.mc.steamadvantage.graphics.DrillBitRenderer";
    private static final String DRILL_RENDERER = "zone.moddev.mc.electricadvantage.graphics.LaserDrillRenderer";
    private static final String TURRET_RENDERER = "zone.moddev.mc.electricadvantage.graphics.LaserTurretRenderer";
    private static final String TURBINE_RENDERER = "zone.moddev.mc.electricadvantage.graphics.HydroturbineRenderer";

    static void verify(Minecraft minecraft, JsonObject result) {
        JsonObject report = new JsonObject();
        JsonArray cases = new JsonArray();
        report.add("cases", cases);
        report.addProperty("scope", "REGISTERED_RENDERER_CPU_GEOMETRY_NOT_PIXEL_ACCEPTANCE");
        result.add("child_renderer_geometry", report);
        try {
            require(Boolean.getBoolean("advantageworks.requireExtensions"), "Child renderer geometry requires the combined profile");
            require(minecraft.world == null && minecraft.player == null && !minecraft.isIntegratedServerRunning(), "Geometry probe must remain worldless");
            steam(minecraft, cases);
            drill(minecraft, cases);
            turret(minecraft, cases);
            run(cases, "hydroturbine-registration", row -> {
                EntityType<?> type = ForgeRegistries.ENTITIES.getValue(new ResourceLocation("electricadvantage:hydroturbine"));
                require(type != null, "Missing registered hydroturbine type");
                EntityRenderer<?> renderer = minecraft.getRenderManager().renderers.get(type);
                require(renderer != null && TURBINE_RENDERER.equals(renderer.getClass().getName()), "Missing production hydroturbine renderer");
                row.addProperty("renderer", renderer.getClass().getName());
                ResourceLocation texture = (ResourceLocation) valueOfType(renderer, ResourceLocation.class);
                require(new ResourceLocation("electricadvantage:textures/entity/hydroturbine.png").equals(texture), "Wrong hydroturbine texture");
                texture(minecraft, texture, row);
                row.addProperty("status", "MANUAL_GAP_WORLD_REQUIRED");
                row.addProperty("gap", "HydroturbineEntity constructor reads World.rand; orientation/spin vertices require a fresh disposable client world. No entity was constructed, no render invoked, and no geometry pass is claimed.");
            });
            boolean failed = false;
            int passed = 0, gaps = 0;
            for (com.google.gson.JsonElement entry : cases) {
                String status = entry.getAsJsonObject().get("status").getAsString();
                failed |= "FAIL".equals(status);
                if ("PASS".equals(status)) passed++;
                if ("MANUAL_GAP_WORLD_REQUIRED".equals(status)) gaps++;
            }
            report.addProperty("passed_geometry_cases", passed);
            report.addProperty("manual_gaps", gaps);
            require(cases.size() == 26, "Incomplete bounded child renderer case inventory");
            require(!failed, "Registered child renderer geometry failed; inspect child-renderer-geometry.json");
            require(passed == 25 && gaps == 1, "Child renderer cases were skipped or misclassified");
            report.addProperty("status", "PASS_WITH_MANUAL_GAPS");
            result.addProperty("child_renderer_geometry_status", "PASS_WITH_MANUAL_GAPS");
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            report.addProperty("status", "FAIL");
            report.addProperty("error", failure.toString());
            result.addProperty("child_renderer_geometry_status", "FAIL");
            throw failure;
        } finally {
            try { Files.write(Paths.get("child-renderer-geometry.json"), report.toString().getBytes(StandardCharsets.UTF_8)); }
            catch (IOException failure) { throw new IllegalStateException("Could not retain child renderer geometry evidence", failure); }
        }
    }

    private static void steam(Minecraft minecraft, JsonArray cases) {
        for (Direction facing : Direction.values()) {
            final List<List<float[]>> frames = new ArrayList<>();
            for (float partial : new float[]{0F, 0.5F}) run(cases, "steam-drillbit-" + facing + "-" + partial, row -> {
                TileEntity tile = tile("steamadvantage:drillbit");
                tile.getClass().getMethod("setDirection", Direction.class).invoke(tile, facing);
                set(tile, "rotation", 0F);
                List<float[]> vertices = render(minecraft, tile, STEAM_RENDERER, partial,
                        "steamadvantage:textures/entity/drill_bit.png", row);
                mesh(vertices, 24);
                int axis = facing.getAxis() == Direction.Axis.X ? 0 : facing.getAxis() == Direction.Axis.Y ? 1 : 2;
                for (float[] vertex : vertices) for (int i = 0; i < 3; i++) require(vertex[i] >= -EPSILON && vertex[i] <= 1 + EPSILON, "Drill bit escaped its block");
                require(close(min(vertices, axis), 0) && close(max(vertices, axis), 1), "Drill bit is not aligned to " + facing.getAxis());
                for (int i = 0; i < 3; i++) if (i != axis) require(max(vertices, i) - min(vertices, i) < 0.72F, "Drill bit cross-section exceeds its rotated radius");
                frames.add(vertices);
                if (partial > 0) require(frames.size() == 2 && different(frames.get(0), vertices), "Steam partial-tick rotation did not move vertices");
            });
        }
    }

    private static void drill(Minecraft minecraft, JsonArray cases) {
        for (Direction facing : Direction.values()) run(cases, "electric-drill-" + facing, row -> {
            TileEntity tile = tile("electricadvantage:electric_drill");
            // These are the production tile's synchronized renderer inputs, not replacement methods.
            set(tile, "facingCache", facing);
            set(tile, "laserLength", 4);
            require(tile.getClass().getMethod("getFacing").invoke(tile) == facing, "Drill fixture facing was not consumed");
            require(((Number) tile.getClass().getMethod("getDrillLength").invoke(tile)).intValue() == 4, "Drill fixture length was not consumed");
            List<float[]> vertices = render(minecraft, tile, DRILL_RENDERER, 0.5F,
                    "electricadvantage:textures/materials/laser_beam.png", row);
            mesh(vertices, 16);
            beam(vertices, facing, new float[]{0.5F, 0.5F, 0.5F}, 4F);
            for (float[] vertex : vertices) require(close(vertex[3], 0) && close(vertex[4], 1), "Drill beam UV layout changed");
        });
    }

    private static void turret(Minecraft minecraft, JsonArray cases) {
        run(cases, "turret-idle", row -> {
            TileEntity tile = tile("electricadvantage:laser_turret");
            set(tile, "rotYaw", -90F);
            set(tile, "rotOldYaw", -90F);
            List<float[]> vertices = render(minecraft, tile, TURRET_RENDERER, 0.5F,
                    "electricadvantage:textures/entity/laser_turret.png", row);
            mesh(vertices, 24);
            require(close(min(vertices, 0), 0.25F) && close(max(vertices, 0), 0.75F)
                    && close(min(vertices, 1), 0.5F) && close(max(vertices, 1), 1F)
                    && close(min(vertices, 2), 0.25F) && close(max(vertices, 2), 0.75F), "Idle turret body bounds changed");
            for (int i = 0; i < 4; i++) require(vertices.get(i)[4] >= 0.5F - EPSILON, "Idle turret uses the active optic UVs");
        });
        for (Direction facing : Direction.values()) run(cases, "turret-target-" + facing, row -> {
            TileEntity tile = tile("electricadvantage:laser_turret");
            PoweredEntity machine = (PoweredEntity) tile;
            machine.setEnergy(2000F, machine.getTypes()[0]);
            float yaw = facing == Direction.EAST ? 0F : facing == Direction.SOUTH ? 90F : facing == Direction.WEST ? 180F : -90F;
            float pitch = facing == Direction.UP ? 90F : facing == Direction.DOWN ? -90F : 0F;
            set(tile, "rotYaw", yaw);
            set(tile, "rotOldYaw", yaw);
            set(tile, "rotPitch", pitch);
            set(tile, "rotOldPitch", pitch);
            set(tile, "targetLocked", true);
            set(tile, "laserAttack", 5);
            set(tile, "laserBlastLength", 4D);
            require(Boolean.TRUE.equals(tile.getClass().getMethod("isActive").invoke(tile))
                    && Boolean.TRUE.equals(tile.getClass().getMethod("showLaserLine").invoke(tile)), "Turret fixture did not enable the production target branch");
            List<float[]> vertices = render(minecraft, tile, TURRET_RENDERER, 0.5F,
                    "electricadvantage:textures/entity/laser_turret.png", row);
            mesh(vertices, 40);
            beam(vertices.subList(24, 40), facing, new float[]{0.5F, 0.75F, 0.5F}, 4F);
            for (float[] vertex : vertices.subList(0, 24)) for (int i = 0; i < 3; i++)
                require(Math.abs(vertex[i] - (i == 1 ? 0.75F : 0.5F)) <= 0.434F, "Turret body escaped its rotation bounds");
            for (int i = 0; i < 4; i++) require(vertices.get(i)[4] <= 0.5F + EPSILON, "Active turret uses the idle optic UVs");
        });
    }

    private static TileEntity tile(String id) {
        TileEntityType<?> type = ForgeRegistries.TILE_ENTITIES.getValue(new ResourceLocation(id));
        require(type != null, "Missing registered tile type " + id);
        TileEntity tile = type.create();
        require(tile != null && tile.getWorld() == null, "Tile factory must create a worldless native fixture");
        return tile;
    }

    private static List<float[]> render(Minecraft minecraft, TileEntity tile, String expectedRenderer,
            float partial, String expectedTexture, JsonObject row) throws Exception {
        TileEntityRenderer<TileEntity> renderer = TileEntityRendererDispatcher.instance.getRenderer(tile);
        require(renderer != null && expectedRenderer.equals(renderer.getClass().getName()), "Dispatcher has no expected production renderer: " + expectedRenderer);
        row.addProperty("renderer", renderer.getClass().getName());
        row.addProperty("partial_tick", partial);
        MatrixStack matrices = new MatrixStack();
        MatrixStack.Entry original = matrices.getLast();
        net.minecraft.client.renderer.Matrix4f position = original.getMatrix().copy();
        net.minecraft.client.renderer.Matrix3f normal = original.getNormal().copy();
        Capture buffers = new Capture(minecraft, new ResourceLocation(expectedTexture), row);
        boolean balanced;
        Throwable renderFailure = null;
        try {
            renderer.render(tile, partial, matrices, buffers, LIGHT, OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            renderFailure = failure;
            row.addProperty("render_error", failure.toString());
            throw failure;
        } finally {
            balanced = matrices.clear() && matrices.getLast() == original
                    && position.equals(original.getMatrix()) && normal.equals(original.getNormal());
            row.addProperty("matrix_balanced", balanced);
            try { buffers.finish(); }
            catch (Exception failure) {
                if (renderFailure == null) throw failure;
                renderFailure.addSuppressed(failure);
                row.addProperty("capture_error", failure.toString());
            }
        }
        require(balanced, "Production renderer did not restore its matrix stack");
        row.add("bounds_xyz", ranges(buffers.vertices, 0, 3));
        row.add("uv_bounds", ranges(buffers.vertices, 3, 2));
        return buffers.vertices;
    }

    private static final class Capture implements IRenderTypeBuffer {
        private final Minecraft minecraft;
        private final ResourceLocation expectedTexture;
        private final JsonArray batches = new JsonArray();
        private final Map<RenderType, BufferBuilder> buffers = new LinkedHashMap<>();
        private final List<float[]> vertices = new ArrayList<>();

        Capture(Minecraft minecraft, ResourceLocation expectedTexture, JsonObject row) {
            this.minecraft = minecraft;
            this.expectedTexture = expectedTexture;
            row.add("native_buffers", batches);
        }

        @Override public IVertexBuilder getBuffer(RenderType type) {
            require(type.getDrawMode() == 7 && type.getVertexFormat().equals(DefaultVertexFormats.ENTITY), "Unexpected production renderer vertex format/mode");
            BufferBuilder buffer = buffers.get(type);
            if (buffer == null) {
                require(buffers.size() < 8, "Renderer requested too many buffers");
                buffer = new BufferBuilder(4096);
                buffer.begin(type.getDrawMode(), type.getVertexFormat());
                buffers.put(type, buffer);
            }
            return buffer;
        }

        void finish() throws Exception {
            for (Map.Entry<RenderType, BufferBuilder> entry : buffers.entrySet()) {
                BufferBuilder buffer = entry.getValue();
                buffer.finishDrawing();
                Pair<BufferBuilder.DrawState, ByteBuffer> built = buffer.getNextBuffer();
                int count = built.getFirst().getVertexCount();
                JsonObject batch = new JsonObject();
                batches.add(batch);
                batch.addProperty("vertex_count", count);
                batch.addProperty("format", built.getFirst().getFormat().toString());
                JsonArray raw = new JsonArray();
                batch.add("vertices_xyz_uv_normal_alpha", raw);
                require(count <= 4096, "Renderer exceeded the bounded geometry fixture");
                VertexFormat format = built.getFirst().getFormat();
                ByteBuffer data = built.getSecond();
                int p = offset(format, VertexFormatElement.Usage.POSITION, 0);
                int uv = offset(format, VertexFormatElement.Usage.UV, 0);
                int n = offset(format, VertexFormatElement.Usage.NORMAL, 0);
                int c = offset(format, VertexFormatElement.Usage.COLOR, 0);
                for (int i = 0; i < count; i++) {
                    int base = i * format.getSize();
                    float[] vertex = {data.getFloat(base + p), data.getFloat(base + p + 4), data.getFloat(base + p + 8),
                            data.getFloat(base + uv), data.getFloat(base + uv + 4), data.get(base + n) / 127F,
                            data.get(base + n + 1) / 127F, data.get(base + n + 2) / 127F, (data.get(base + c + 3) & 255) / 255F};
                    vertices.add(vertex);
                    JsonArray values = new JsonArray();
                    for (float value : vertex) {
                        if (Float.isFinite(value)) values.add(value); else values.add(String.valueOf(value));
                    }
                    raw.add(values);
                }
                // Inspect the texture actually selected by the production RenderType, not a substitute type.
                Object state = valueOfType(entry.getKey(), RenderType.State.class);
                Object textureState = valueOfType(state, RenderState.TextureState.class);
                Optional<?> selected = (Optional<?>) valueOfType(textureState, Optional.class);
                require(selected.isPresent() && selected.get() instanceof ResourceLocation, "Production render type has no texture");
                ResourceLocation selectedTexture = (ResourceLocation) selected.get();
                batch.addProperty("texture", selectedTexture.toString());
                require(expectedTexture.equals(selectedTexture), "Production renderer selected the wrong texture");
                texture(minecraft, selectedTexture, batch);
            }
        }
    }

    private static void mesh(List<float[]> vertices, int count) {
        require(vertices.size() == count, "Unexpected geometry vertex count: " + vertices.size() + ", expected " + count);
        for (float[] v : vertices) {
            for (float value : v) require(Float.isFinite(value), "Renderer emitted a non-finite vertex attribute");
            require(v[3] >= -EPSILON && v[3] <= 1 + EPSILON && v[4] >= -EPSILON && v[4] <= 1 + EPSILON, "Renderer UV escaped its texture");
            float length = v[5] * v[5] + v[6] * v[6] + v[7] * v[7];
            require(length > 0.9F && length < 1.1F && v[8] > 0F, "Renderer emitted an invalid normal or invisible vertex");
        }
        for (int i = 0; i < vertices.size(); i += 4) {
            float[] a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
            float x = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
            float y = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
            float z = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
            require(x * x + y * y + z * z > 0.00000001F, "Renderer emitted a blank/degenerate quad");
        }
    }

    private static void beam(List<float[]> vertices, Direction facing, float[] origin, float length) {
        int axis = facing.getAxis() == Direction.Axis.X ? 0 : facing.getAxis() == Direction.Axis.Y ? 1 : 2;
        int sign = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
        float start = Float.POSITIVE_INFINITY, end = Float.NEGATIVE_INFINITY;
        for (float[] vertex : vertices) {
            float along = (vertex[axis] - origin[axis]) * sign;
            start = Math.min(start, along);
            end = Math.max(end, along);
            require(along >= -EPSILON && along <= length + EPSILON, "Beam points away from " + facing);
            for (int i = 0; i < 3; i++) if (i != axis) require(Math.abs(vertex[i] - origin[i]) <= 0.125F + EPSILON, "Beam radius/axis changed");
        }
        require(close(start, 0) && close(end, length), "Beam cardinal bounds/length changed");
    }

    private static void texture(Minecraft minecraft, ResourceLocation location, JsonObject row) throws IOException {
        try (IResource resource = minecraft.getResourceManager().getResource(location)) {
            BufferedImage image = ImageIO.read(resource.getInputStream());
            require(image != null && image.getWidth() > 0 && image.getHeight() > 0, "Renderer texture is not a readable image: " + location);
            row.addProperty("texture", location.toString());
            row.addProperty("texture_width", image.getWidth());
            row.addProperty("texture_height", image.getHeight());
        }
    }

    private static Object valueOfType(Object object, Class<?> type) throws IllegalAccessException {
        Field found = null;
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) for (Field field : owner.getDeclaredFields()) {
            if (field.getType() == type) {
                require(found == null, "Ambiguous renderer field type " + type.getName());
                found = field;
            }
        }
        require(found != null, "Missing renderer field type " + type.getName());
        found.setAccessible(true);
        return found.get(Modifier.isStatic(found.getModifiers()) ? null : object);
    }

    private static void set(Object object, String name, Object value) throws ReflectiveOperationException {
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) {
            try {
                Field field = owner.getDeclaredField(name);
                require(!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()), "Fixture may only set mutable instance inputs");
                field.setAccessible(true);
                field.set(object, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(object.getClass().getName() + "." + name);
    }

    private static int offset(VertexFormat format, VertexFormatElement.Usage usage, int index) {
        for (int i = 0; i < format.getElements().size(); i++) {
            VertexFormatElement element = format.getElements().get(i);
            if (element.getUsage() == usage && element.getIndex() == index) return format.getOffset(i);
        }
        throw new IllegalStateException("Native vertex format lacks " + usage + " " + index);
    }

    private static boolean different(List<float[]> a, List<float[]> b) {
        if (a.size() != b.size()) return true;
        for (int i = 0; i < a.size(); i++) for (int j = 0; j < 3; j++) if (!close(a.get(i)[j], b.get(i)[j])) return true;
        return false;
    }

    private static JsonArray ranges(List<float[]> vertices, int first, int count) {
        JsonArray result = new JsonArray();
        for (int i = first; i < first + count; i++) {
            JsonArray range = new JsonArray();
            float low = min(vertices, i), high = max(vertices, i);
            if (Float.isFinite(low)) range.add(low); else range.add(String.valueOf(low));
            if (Float.isFinite(high)) range.add(high); else range.add(String.valueOf(high));
            result.add(range);
        }
        return result;
    }

    private static float min(List<float[]> vertices, int axis) {
        float value = Float.POSITIVE_INFINITY;
        for (float[] vertex : vertices) value = Math.min(value, vertex[axis]);
        return value;
    }

    private static float max(List<float[]> vertices, int axis) {
        float value = Float.NEGATIVE_INFINITY;
        for (float[] vertex : vertices) value = Math.max(value, vertex[axis]);
        return value;
    }

    private interface Check { void run(JsonObject row) throws Exception; }

    private static void run(JsonArray cases, String name, Check check) {
        JsonObject row = new JsonObject();
        row.addProperty("case", name);
        cases.add(row);
        try {
            check.run(row);
            if (!row.has("status")) row.addProperty("status", "PASS");
        } catch (Exception | LinkageError | AssertionError failure) {
            row.addProperty("status", "FAIL");
            row.addProperty("error", failure.toString());
        }
    }

    private static boolean close(float a, float b) { return Math.abs(a - b) <= EPSILON; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
