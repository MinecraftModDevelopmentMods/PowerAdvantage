package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.lib.data.SharedStrings;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.poweradvantage.PowerAdvantage;

public class ItemGroups extends com.mcmoddev.lib.init.ItemGroups {
		private ItemGroups() {
			throw new IllegalAccessError(SharedStrings.NOT_INSTANTIABLE);
		}

		/**
		 *
		 */
		public static void init() {
			//
		}

		public static void setupIcons(String materialName) {
			if (materialName.equalsIgnoreCase("pa-machines")) {
				MMDMaterial mat = Materials.getMaterialByName(materialName);
				getTab(PowerAdvantage.MODID, "tab").setTabIconItem(mat.getBlock("infinite_steam"));
			}
		}
	}
