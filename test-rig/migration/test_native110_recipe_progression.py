"""Synthetic documents test validators only; no native recipe qualification is implied."""
from copy import deepcopy
from pathlib import Path
import unittest
import native110_recipe_progression as native

def fixture(mode="NORMAL"):
    rows=[]
    ores={"ingotIron":["minecraft:iron_ingot@0"],
          "ingotSilicon":["electricadvantage:silicon_ingot@0"],
          "nuggetCopper":["basemetals:copper_nugget@0"],
          "nuggetTin":["basemetals:tin_nugget@0"],
          "nuggetGold":["minecraft:gold_nugget@0"],
          "plastic":["poweradvantage:plastic@0"],"dustRedstone":["minecraft:redstone@0"]}
    def recipe(output,inputs,count=1):
        rows.append(dict(id="craft/"+str(len(rows)),output=output,inputs=inputs,count=count,
                         providers=[[item] for item in inputs],
                         actualCrafted=output,actualCraftedCount=count,
                         status="PASS",owned=True,negativeControlsRejected=2,consumption="NATIVE_SLOT_CRAFTING",
                         recipeBookCraftUnlock="N/A_NO_1_10_RECIPE_BOOK"))
    recipe("poweradvantage:fluid_pipe@0",["minecraft:iron_ingot@0"]*6,3 if mode=="APOCALYPTIC" else 6)
    recipe("steamadvantage:steam_governor@0",["minecraft:iron_nugget@0"])
    recipe("electricadvantage:psu@0",["electricadvantage:control_circuit@0"])
    if mode!="APOCALYPTIC":
        recipe("poweradvantage:sprocket@0",["basemetals:steel_ingot@0"],4)
        for metal in ("Copper","Tin","Gold"):
            inputs=[ores["ingotSilicon"][0]]*3+[ores["nugget"+metal][0]]*3
            if mode=="TECH_PROGRESSION":
                inputs=[ores["plastic"][0],ores["dustRedstone"][0],ores["plastic"][0]]+inputs
            recipe("electricadvantage:integrated_circuit@0",inputs,3)
        recipe("electricadvantage:photovoltaic_generator@0",["minecraft:glass@0"])
    return dict(minecraft=native.MC,mode=mode,rubber=True,status="EXPORTED_NOT_QUALIFIED",crafting=rows,
                targets=sorted(native.CORE),ores=ores,
                blockItems=[],smelting=[dict(input="minecraft:iron_ore@0",output="minecraft:iron_ingot@0",count=1)],
                crusherRegistry="unit:fixture",crushing=[dict(input="minecraft:potato@0",output="poweradvantage:starch@0",tools=[])],sampledNativeLoot=[])

class RecipeAuditTests(unittest.TestCase):
    def test_missing_process_functions_and_lookup_errors_remain_required_failures(self):
        for mode in native.MODES:
            for field,value in (("smelting",[]),("crushing",[]),("crusherRegistry","MISSING"),
                                ("crusherLookupError","java.lang.NullPointerException")):
                doc=fixture(mode);doc[field]=value
                with self.subTest(mode=mode,field=field),self.assertRaisesRegex(AssertionError,"smelting/crusher"):
                    native.mode_checks(doc,mode,True)

    def test_actual_mode_contracts_cover_all_three_modes(self):
        self.assertEqual(("NORMAL","TECH_PROGRESSION","APOCALYPTIC"),native.MODES)
        for mode in native.MODES: native.mode_checks(fixture(mode),mode,True)

    def test_wrong_mode_rubber_engine_or_empty_registry_is_rejected(self):
        for field,value in (("mode","TECH_PROGRESSION"),("rubber",False),("minecraft","1.13.2"),("crafting",[]),("targets",[])):
            doc=fixture();doc[field]=value
            with self.subTest(field=field),self.assertRaises(AssertionError): native.mode_checks(doc,"NORMAL",True)

    def test_missing_native_consumption_or_negative_control_is_rejected(self):
        for field,value in (("consumption","RESULT_ONLY"),("negativeControlsRejected",0),("recipeBookCraftUnlock","NOT_TESTED"),("count",0)):
            doc=fixture();doc["crafting"][0][field]=value
            with self.subTest(field=field),self.assertRaises(AssertionError): native.mode_checks(doc,"NORMAL",True)

    def test_missing_core_or_duplicate_recipe_cannot_qualify(self):
        doc=fixture();doc["targets"].pop()
        with self.assertRaises(AssertionError): native.mode_checks(doc,"NORMAL",True)
        doc=fixture();doc["crafting"].append(deepcopy(doc["crafting"][0]))
        with self.assertRaises(AssertionError): native.mode_checks(doc,"NORMAL",True)

    def test_apocalyptic_does_not_gain_free_chips_sprockets_or_solar(self):
        for target in ("poweradvantage:sprocket@0","electricadvantage:integrated_circuit@0","electricadvantage:photovoltaic_generator@0"):
            doc=fixture("APOCALYPTIC")
            extra=deepcopy(doc["crafting"][0]);extra.update(id="forbidden",output=target);doc["crafting"].append(extra)
            with self.subTest(target=target),self.assertRaises(AssertionError): native.mode_checks(doc,"APOCALYPTIC",True)

    def test_pipe_yields_and_tech_chip_complexity_remain_strict(self):
        for mode in native.MODES:
            doc=fixture(mode);doc["crafting"][0]["count"]=99
            with self.subTest(mode=mode),self.assertRaises(AssertionError):native.mode_checks(doc,mode,True)
        doc=fixture("TECH_PROGRESSION")
        next(r for r in doc["crafting"] if r["output"]=="electricadvantage:integrated_circuit@0")["inputs"]=["minecraft:dirt@0"]*6
        with self.assertRaises(AssertionError): native.mode_checks(doc,"TECH_PROGRESSION",True)

    def test_wrong_chip_materials_are_not_blessed_by_a_root_flag(self):
        for mode in ("NORMAL","TECH_PROGRESSION"):
            doc=fixture(mode);doc["chipSemanticsVerified"]=True
            chip=next(r for r in doc["crafting"] if r["output"]=="electricadvantage:integrated_circuit@0")
            chip["inputs"]=["minecraft:dirt@0"]*len(chip["inputs"])
            chip["providers"]=[[item] for item in chip["inputs"]]
            with self.subTest(mode=mode),self.assertRaisesRegex(AssertionError,"Microchip"):
                native.mode_checks(doc,mode,True)

    def test_chip_yield_is_three_even_when_native_count_fields_agree(self):
        for mode in ("NORMAL","TECH_PROGRESSION"):
            for count in (1,2,4,64):
                doc=fixture(mode)
                chip=next(r for r in doc["crafting"] if r["output"]=="electricadvantage:integrated_circuit@0")
                chip.update(count=count,actualCraftedCount=count)
                with self.subTest(mode=mode,count=count),self.assertRaisesRegex(AssertionError,"Microchip"):
                    native.mode_checks(doc,mode,True)

    def test_chip_nuggets_cannot_be_mixed_or_replaced_by_ingots(self):
        for mode in ("NORMAL","TECH_PROGRESSION"):
            for metals in (["basemetals:copper_nugget@0","basemetals:tin_nugget@0","minecraft:gold_nugget@0"],
                           ["basemetals:copper_ingot@0"]*3):
                doc=fixture(mode);doc["ores"]["ingotCopper"]=["basemetals:copper_ingot@0"]
                chip=next(r for r in doc["crafting"] if r["output"]=="electricadvantage:integrated_circuit@0")
                chip["inputs"][-3:]=metals;chip["providers"][-3:]=[[item] for item in metals]
                with self.subTest(mode=mode,metals=metals),self.assertRaisesRegex(AssertionError,"Microchip"):
                    native.mode_checks(doc,mode,True)

    def test_chip_requires_actual_native_ore_entries(self):
        for mode in ("NORMAL","TECH_PROGRESSION"):
            for ore in ("ingotSilicon","nuggetCopper"):
                doc=fixture(mode);doc["ores"].pop(ore)
                with self.subTest(mode=mode,ore=ore),self.assertRaisesRegex(AssertionError,"Microchip"):
                    native.mode_checks(doc,mode,True)

    def test_tech_chip_requires_plastic_and_redstone(self):
        for slot in (0,1,2):
            doc=fixture("TECH_PROGRESSION")
            chip=next(r for r in doc["crafting"] if r["output"]=="electricadvantage:integrated_circuit@0")
            chip["inputs"][slot]=doc["ores"]["ingotSilicon"][0]
            chip["providers"][slot]=[chip["inputs"][slot]]
            with self.subTest(slot=slot),self.assertRaisesRegex(AssertionError,"Microchip"):
                native.mode_checks(doc,"TECH_PROGRESSION",True)

    def test_chip_requires_complete_consistent_provider_evidence(self):
        for mode in ("NORMAL","TECH_PROGRESSION"):
            for mutation in ("missing","short","empty","wrong-selected","extra-wrong-choice"):
                doc=fixture(mode)
                chip=next(r for r in doc["crafting"] if r["output"]=="electricadvantage:integrated_circuit@0")
                if mutation=="missing": chip.pop("providers")
                elif mutation=="short": chip["providers"].pop()
                elif mutation=="empty": chip["providers"][0]=[]
                elif mutation=="wrong-selected":
                    alternate="othermod:valid_chip_ingredient@0"
                    ore="plastic" if mode=="TECH_PROGRESSION" else "ingotSilicon"
                    doc["ores"][ore].append(alternate);chip["inputs"][0]=alternate
                else: chip["providers"][0].append("minecraft:dirt@0")
                with self.subTest(mode=mode,mutation=mutation),self.assertRaisesRegex(AssertionError,"Microchip"):
                    native.mode_checks(doc,mode,True)

    def test_chip_uses_actual_ore_entries_not_item_name_heuristics(self):
        for mode in ("NORMAL","TECH_PROGRESSION"):
            doc=fixture(mode)
            replacements={values[0]:"othermod:material_"+str(i)+"@7"
                          for i,values in enumerate(doc["ores"].values())}
            doc["ores"]={name:[replacements[item] for item in entries] for name,entries in doc["ores"].items()}
            for row in doc["crafting"]:
                row["inputs"]=[replacements.get(item,item) for item in row["inputs"]]
                row["providers"]=[[replacements.get(item,item) for item in choices] for choices in row["providers"]]
            native.mode_checks(doc,mode,True)

    def test_chip_wildcard_ores_accept_native_expanded_providers(self):
        doc=fixture();doc["ores"]["ingotSilicon"]=["othermod:silicon@32767"]
        for chip in (r for r in doc["crafting"] if r["output"]=="electricadvantage:integrated_circuit@0"):
            chip["inputs"][:3]=["othermod:silicon@5"]*3
            chip["providers"][:3]=[["othermod:silicon@5","othermod:silicon@7"] for _ in range(3)]
        native.mode_checks(doc,"NORMAL",True)

    def test_chip_provider_wildcards_cannot_broaden_specific_ore_entries(self):
        doc=fixture()
        chip=next(r for r in doc["crafting"] if r["output"]=="electricadvantage:integrated_circuit@0")
        chip["providers"][0]=[chip["inputs"][0].rsplit("@",1)[0]+"@32767"]
        with self.assertRaisesRegex(AssertionError,"Microchip"):
            native.mode_checks(doc,"NORMAL",True)

    def test_unseeded_self_and_mutual_cycles_are_not_reachable(self):
        doc=fixture()
        for output,inputs in (("poweradvantage:a@0",["poweradvantage:b@0"]),("poweradvantage:b@0",["poweradvantage:a@0"])):
            doc["crafting"].append(dict(id=output,output=output,inputs=inputs,status="PASS"))
            doc["targets"].append(output)
        result=native.reachability(doc)
        self.assertIn("poweradvantage:a@0",result["blocked"]);self.assertIn("poweradvantage:b@0",result["blocked"])

    def test_generated_loot_configuration_is_not_a_real_acquisition(self):
        doc=fixture("APOCALYPTIC");doc["generatedLootConfig"]=["poweradvantage:sprocket@0"]
        self.assertNotIn("poweradvantage:sprocket@0",native.reachability(doc)["reachable"])
        doc["sampledNativeLoot"]=[dict(item="poweradvantage:sprocket@0",table="simple_dungeon",seed=4)]
        self.assertIn("poweradvantage:sprocket@0",native.reachability(doc)["reachable"])
        doc["mode"]="NORMAL"
        self.assertNotIn("poweradvantage:sprocket@0",native.reachability(doc)["reachable"])

    def test_ore_roots_never_seed_a_crafted_nonblock_part(self):
        doc=fixture();doc["ores"]["oreCopper"]=["poweradvantage:sprocket@0"]
        self.assertNotIn("poweradvantage:sprocket@0",native.reachability(doc)["reachable"])

    def test_smelting_requires_a_reachable_furnace_and_fuel(self):
        doc=fixture();doc["smelting"]=[dict(input="minecraft:iron_ore@0",output="minecraft:iron_ingot@0",count=1)]
        self.assertNotIn("minecraft:iron_ingot@0",native.reachability(doc)["reachable"])
        doc["crafting"].append(dict(id="furnace",inputs=["minecraft:cobblestone@0"]*8,output="minecraft:furnace@0",status="PASS"))
        self.assertIn("minecraft:iron_ingot@0",native.reachability(doc)["reachable"])

    def test_crushing_requires_an_obtainable_valid_hammer(self):
        doc=fixture();doc["crushing"]=[dict(input="minecraft:potato@0",output="poweradvantage:starch@0",tools=["basemetals:stone_crackhammer@0"])]
        self.assertNotIn("poweradvantage:starch@0",native.reachability(doc)["reachable"])
        doc["crafting"].append(dict(id="hammer",inputs=["minecraft:cobblestone@0"],output="basemetals:stone_crackhammer@0",status="PASS"))
        self.assertIn("poweradvantage:starch@0",native.reachability(doc)["reachable"])

    def test_unverified_native_recipes_and_missing_machine_edges_are_explicit(self):
        doc=fixture();doc["crafting"][0].update(status="UNVERIFIED",reason="Missing provider")
        result=native.reachability(doc)
        self.assertEqual("NOT_TESTED",result["machineProduction"])
        self.assertTrue(result["unverifiedNativeRecipes"]);self.assertTrue(result["practicalGaps"])
        self.assertNotIn("minecraft:water_bucket@0",result["reachable"])

    def test_native_helper_and_runner_use_real_callbacks_and_never_register_themselves(self):
        root=Path(__file__).resolve().parents[1]
        java=(root/"src/main/java/zone/moddev/mc/advantageworks/NativeRecipeProgressionProbe.java").read_text()
        for text in ("recipe.matches(grid,world)","recipe.getCraftingResult(grid)","new SlotCrafting(","negativeControlsRejected"):
            self.assertIn(text,java)
        runner=Path(native.__file__).read_text()
        for text in ('"auditStatus"','"completeProgression":"PARTIAL"', 'verify_code(runtime,code)', 'return 1 if report["status"]=="FAIL" else 2'):
            self.assertIn(text,runner)
        self.assertNotIn("tickUpdate(",java)
        self.assertIn("recipe.getRemainingItems(grid)",java)
        self.assertIn("((ShapedRecipes)recipe).recipeItems",java)
        self.assertIn("((ShapelessRecipes)recipe).recipeItems",java)
        self.assertIn("((ShapedOreRecipe)recipe).getInput()",java)
        self.assertIn("((ShapelessOreRecipe)recipe).getInput()",java)
        self.assertNotIn('getMethod("getRemainingItems"',java)
        self.assertNotIn('getField("recipeItems")',java)

if __name__=="__main__": unittest.main()
