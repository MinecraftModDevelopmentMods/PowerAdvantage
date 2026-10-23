package zone.moddev.mc.advantageworks.client;

import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import zone.moddev.mc.poweradvantage.init.ItemGroups;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Runs after native resource baking, without opening or generating a world. */
@Mod(ClientResourceProbe.MODID)
@Mod.EventBusSubscriber(modid = ClientResourceProbe.MODID, value = Dist.CLIENT)
public final class ClientResourceProbe {
    static final String MODID = "advantageclientresources";
    private static boolean finished;
    private static int ticks;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("advantageworks.clientResources") || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (++ticks < 5 || (minecraft.getModelManager().getMissingModel() == null && ticks < 1200)) return;
        finished = true;
        JsonObject result = new JsonObject();
        try {
            com.google.gson.JsonArray mods = new com.google.gson.JsonArray();
            JsonObject versions = new JsonObject();
            for (String id : new String[]{"poweradvantage", "steamadvantage", "electricadvantage"}) {
                boolean loaded = net.minecraftforge.fml.ModList.get().isLoaded(id);
                if (loaded) {
                    mods.add(id);
                    versions.addProperty(id, net.minecraftforge.fml.ModList.get().getModContainerById(id).get()
                            .getModInfo().getVersion().toString());
                }
                if (Boolean.getBoolean("advantageworks.requireExtensions")) require(loaded, "Combined launch omitted " + id);
            }
            result.add("advantage_mods", mods);
            result.add("mod_versions", versions);
            require(versions.has("poweradvantage") && zone.moddev.mc.poweradvantage.PowerAdvantage.VERSION
                    .equals(versions.get("poweradvantage").getAsString()), "Power metadata does not match the compiled version");
            require(minecraft.getModelManager().getMissingModel() != null, "Timed out before model baking");
            int items = 0;
            int states = 0;
            IBakedModel missing = minecraft.getModelManager().getMissingModel();
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = item.getRegistryName();
                if (id == null || !included(id.getNamespace())) continue;
                ItemStack stack = new ItemStack(item);
                String key = item.getTranslationKey(stack);
                require(!I18n.format(key).equals(key), "Untranslated item: " + id + " -> " + key);
                IBakedModel model = minecraft.getItemRenderer().getItemModelWithOverrides(stack, null, null);
                verifyModel(model, missing, null, id.toString(), true);
                items++;
            }
            for (Block block : ForgeRegistries.BLOCKS) {
                ResourceLocation id = block.getRegistryName();
                if (id == null || !included(id.getNamespace())) continue;
                for (IBlockState state : block.getStateContainer().getValidStates()) {
                    IBakedModel model = minecraft.getModelManager().getBlockModelShapes().getModel(state);
                    verifyModel(model, missing, state, state.toString(), !(block instanceof net.minecraft.block.BlockFlowingFluid));
                    states++;
                }
            }
            NonNullList<ItemStack> contents = NonNullList.create();
            ItemGroups.tab_powerAdvantage.fill(contents);
            require(items > 0 && states > 0, "Power Advantage content is not registered");
            require(!contents.isEmpty(), "Power Advantage creative tab is empty");
            require(!ItemGroups.tab_powerAdvantage.getIcon().isEmpty(), "Power Advantage creative tab has no icon");
            require(I18n.format("block.poweradvantage.still").equals(I18n.format("tile.poweradvantage.still.name")),
                    "Legacy GUI translation differs from modern block name");
            TankVisualProbe.verify(minecraft, result);
            OilRenderProbe.verify(minecraft, result);
            RecipeBookGroupingProbe.verify(minecraft, result);
            result.addProperty("status", "PASS");
            result.addProperty("items", items);
            result.addProperty("block_states", states);
            result.addProperty("creative_entries", contents.size());
            result.addProperty("screen", minecraft.currentScreen == null ? "none" : minecraft.currentScreen.getClass().getName());
            LogManager.getLogger(MODID).info("ADVANTAGE_CLIENT_RESOURCES PASS items={} block_states={} creative_entries={}", items, states, contents.size());
        } catch (RuntimeException | AssertionError failure) {
            result.addProperty("status", "FAIL");
            result.addProperty("error", failure.toString());
            LogManager.getLogger(MODID).error("ADVANTAGE_CLIENT_RESOURCES FAIL", failure);
        } finally {
            try { Files.write(Paths.get("client-resources.json"), result.toString().getBytes(StandardCharsets.UTF_8)); }
            catch (IOException failure) { throw new IllegalStateException("Could not write client resource result", failure); }
            finally { minecraft.shutdown(); }
        }
    }

    private static boolean included(String namespace) {
        return "poweradvantage".equals(namespace) || (Boolean.getBoolean("advantageworks.requireExtensions")
                && ("steamadvantage".equals(namespace) || "electricadvantage".equals(namespace)));
    }

    private static void verifyModel(IBakedModel model, IBakedModel missing, IBlockState state, String id, boolean requireQuads) {
        require(model != missing, "Missing baked model: " + id);
        require(!"missingno".equals(model.getParticleTexture().getName().getPath()), "Missing particle texture: " + id);
        Random random = new Random(42L);
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, null, random));
        for (EnumFacing side : EnumFacing.values()) quads.addAll(model.getQuads(state, side, random));
        require(!requireQuads || !quads.isEmpty(), "Empty baked model: " + id);
        for (BakedQuad quad : quads) require(!"missingno".equals(quad.getSprite().getName().getPath()), "Missing face texture: " + id);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
