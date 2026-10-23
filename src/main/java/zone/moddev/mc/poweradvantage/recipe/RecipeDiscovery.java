package zone.moddev.mc.poweradvantage.recipe;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Retains earned discoveries when recipe modes or material providers change. */
public final class RecipeDiscovery {
    private static final Set<String> NAMESPACES = new HashSet<>(Arrays.asList(
            "poweradvantage", "steamadvantage", "electricadvantage"));

    private RecipeDiscovery() {}

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        // Disabled recipes have no reward to grant. Replay only already-earned
        // discoveries when that exact recipe becomes available on a later login.
        for (IRecipe recipe : player.getServer().getRecipeManager().getRecipes()) {
            if (!NAMESPACES.contains(recipe.getId().getNamespace()) || recipe.isDynamic()
                    || player.getRecipeBook().isUnlocked(recipe)) continue;
            Advancement advancement = player.getServer().getAdvancementManager().getAdvancement(
                    new ResourceLocation(recipe.getId().getNamespace(), "recipes/" + recipe.getId().getPath()));
            if (advancement != null && player.getAdvancements().getProgress(advancement).isDone()) {
                player.unlockRecipes(Collections.singletonList(recipe));
            }
        }
    }
}
