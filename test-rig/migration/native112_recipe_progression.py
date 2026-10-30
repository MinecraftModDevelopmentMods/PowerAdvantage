"""Bounded native legacy recipe audit; does NOT qualify live machine production."""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import socket
import traceback

from rig_transport import digest, write_json
from native112 import output_path, pinned_profile, require_disjoint
from native112_code import INVENTORY, Server, verify_code
from native112_lifecycle import prepare, read_json, require_native_boot, verify_staged

MODES=("NORMAL","TECH_PROGRESSION","APOCALYPTIC")
MC="1.12.2"
RAW=("log","sapling","cobblestone","coal","iron_ore","gold_ore","sand","clay_ball","gravel","redstone",
     "diamond","emerald","obsidian","potato","poisonous_potato","wheat","reeds","gunpowder","flint",
     "leather","string","feather","blaze_rod","ender_pearl","quartz","bone","cactus","dirt")
ORE_ROOTS=("oreCopper","oreTin","oreZinc","oreLead","oreNickel","oreSilver","oreLithium","oreSulfur")
EXCLUSIONS={
 "poweradvantage:infinite_steam@0":"Creative-only source",
 "poweradvantage:infinite_electricity@0":"Creative-only source",
 "poweradvantage:infinite_quantum@0":"Creative-only source",
 "poweradvantage:crude_oil@0":"World fluid identity; natural collection is outside this audit",
 "poweradvantage:refined_oil@0":"World fluid identity; live distillation is outside this audit",
 "poweradvantage:fluid_pipe_terminal@0":"Hidden ordinary-pipe state",
 "steamadvantage:drillbit@0":"Drill-created helper",
 "steamadvantage:platform@0":"Elevator-created helper",
 "steamadvantage:pump_pipe_steam@0":"Pump-created helper",
 "electricadvantage:pump_pipe_electric@0":"Pump-created helper",
 "electricadvantage:laser_turret_evil@0":"Hidden converted turret state",
}
CORE={"poweradvantage:sprocket@0","poweradvantage:fluid_pipe@0","steamadvantage:steam_governor@0",
      "steamadvantage:steam_pipe@0","electricadvantage:integrated_circuit@0","electricadvantage:psu@0",
      "electricadvantage:electric_conduit@0","electricadvantage:photovoltaic_generator@0"}


def mode_checks(doc,mode,rubber):
    if (doc.get("minecraft")!=MC or doc.get("mode")!=mode or doc.get("rubber") is not rubber
            or doc.get("status")!="EXPORTED_NOT_QUALIFIED" or not doc.get("crafting") or not doc.get("targets")):
        raise AssertionError("Wrong engine/mode/configuration or empty native recipe audit")
    if not CORE <= set(doc["targets"]): raise AssertionError("Critical registered components are missing")
    if not doc.get("smelting") or not doc.get("crushing") or doc.get("crusherRegistry") in (None,"MISSING") or doc.get("crusherLookupError"):
        raise AssertionError("Registered smelting/crusher functions are missing or unsupported")
    crafting=doc["crafting"]
    ids=[r["id"] for r in crafting]
    if len(ids)!=len(set(ids)): raise AssertionError("Duplicate native recipe evidence")
    for row in crafting:
        owned=isinstance(row.get("output"),str) and row["output"].startswith(("poweradvantage:","steamadvantage:","electricadvantage:"))
        if row.get("owned") is not owned: raise AssertionError("Native recipe ownership evidence is missing or inconsistent")
        if row.get("status")=="PASS" and (row.get("negativeControlsRejected")!=2
                or type(row.get("count")) is not int or row["count"]<=0 or not row.get("output")
                or not isinstance(row.get("inputs"),list) or not any(row["inputs"])):
            raise AssertionError("Incomplete native match/negative-control evidence")
        if row.get("status")=="PASS" and row.get("owned") is True and row.get("consumption")!="NATIVE_SLOT_CRAFTING":
            raise AssertionError("Owned craft lacks actual native input consumption")
        if row.get("status")=="PASS" and row.get("owned") is True and (row.get("actualCrafted")!=row["output"] or type(row.get("actualCraftedCount")) is not int or row["actualCraftedCount"]!=row["count"]):
            raise AssertionError("Native crafted output identity/count was not verified")
        if row.get("status")=="PASS" and row.get("owned") is True and row.get("recipeBookCraftUnlock")!="PASS":
            raise AssertionError("Missing native recipe-book result or explicit legacy N/A")
    def rows(output): return [r for r in crafting if r.get("output")==output]
    for target in ("poweradvantage:sprocket@0","electricadvantage:integrated_circuit@0","electricadvantage:photovoltaic_generator@0"):
        if bool(rows(target)) != (mode!="APOCALYPTIC"): raise AssertionError("Recipe mode restriction changed: "+target)
    for target in ("steamadvantage:steam_governor@0","electricadvantage:psu@0"):
        if not rows(target): raise AssertionError("Mode lost a foundational recipe: "+target)
    iron=set(doc.get("ores",{}).get("ingotIron",[]))
    pipes=[r for r in rows("poweradvantage:fluid_pipe@0")
           if len([x for x in r.get("inputs",[]) if x])==6
           and all(x in iron for x in r["inputs"] if x)]
    if not pipes or any(r["count"]!=(3 if mode=="APOCALYPTIC" else 6) for r in pipes):
        raise AssertionError("Native iron-pipe yield changed")
    chips=rows("electricadvantage:integrated_circuit@0")
    # Forge may expand a wildcard ore entry into concrete matching-stack variants.
    def matches_provider(value,choices):
        return (isinstance(value,str) and "@" in value and isinstance(choices,list)
                and any(value==choice or value.rsplit("@",1)[0]+"@32767"==choice for choice in choices))
    def matches_slot(value,choices,ore):
        entries=doc.get("ores",{}).get(ore,[])
        return (isinstance(choices,list) and bool(choices)
                and matches_provider(value,entries) and matches_provider(value,choices)
                and all(matches_provider(choice,entries) for choice in choices))
    prefix=["plastic","dustRedstone","plastic"] if mode=="TECH_PROGRESSION" else []
    for chip in chips:
        inputs=chip.get("inputs",[]); providers=chip.get("providers",[])
        if (type(chip.get("count")) is not int or chip["count"]!=3
                or not isinstance(inputs,list) or len(inputs)!=len(prefix)+6):
            raise AssertionError("Microchip mode ingredients or three-chip yield changed")
        if not isinstance(providers,list) or len(providers)!=len(inputs):
            raise AssertionError("Microchip native ingredient providers are missing")
        if not any(all(matches_slot(value,choices,ore) for value,choices,ore in
                       zip(inputs,providers,prefix+["ingotSilicon"]*3+[metal]*3))
                   for metal in ("nuggetCopper","nuggetTin","nuggetGold")):
            raise AssertionError("Microchip ingredients disagree with native ore providers")


def reachability(doc):
    # Availability closure only: renewable raw access, not finite resource quantities.
    registered=set(doc["targets"]) | set(doc.get("blockItems",[]))
    known={"minecraft:"+name+"@0":{"via":"declared_raw_resource","inputs":[]} for name in RAW}
    for name in ORE_ROOTS:
        for token in doc.get("ores",{}).get(name,[]):
            normalized=token.replace("@32767","@0")
            if normalized in set(doc.get("blockItems",[])): known[normalized]={"via":"declared_natural_ore:"+name,"inputs":[]}
    for loot in doc.get("sampledNativeLoot",[]):
        if doc["mode"]=="APOCALYPTIC" and loot.get("item") in set(doc["targets"]):
            known.setdefault(loot["item"],{"via":"sampled_native_chest_loot","table":loot.get("table"),"seed":loot.get("seed"),"inputs":[]})
    changed=True; rounds=0
    while changed:
        changed=False; rounds+=1
        if rounds>100: raise AssertionError("Recipe availability closure failed to converge")
        for row in doc["crafting"]:
            inputs=[x for x in row.get("inputs",[]) if x]
            if row.get("status")=="PASS" and inputs and all(x in known for x in inputs) and row["output"] not in known:
                known[row["output"]]={"via":row["id"],"inputs":inputs}; changed=True
        if "minecraft:furnace@0" in known and any(x in known for x in ("minecraft:coal@0","minecraft:coal@1")):
            for edge in doc.get("smelting",[]):
                if edge["input"] in known and edge["output"] not in known:
                    known[edge["output"]]={"via":"registered_furnace_function","inputs":[edge["input"],"minecraft:furnace@0"]}; changed=True
        for edge in doc.get("crushing",[]):
            tools=[x for x in edge.get("tools",[]) if x in known]
            if edge["input"] in known and tools and edge["output"] not in known:
                known[edge["output"]]={"via":"registered_crusher_function","inputs":[edge["input"],tools[0]]}; changed=True
    blocked=[]; excluded={}
    for target in doc["targets"]:
        if target in EXCLUSIONS: excluded[target]=EXCLUSIONS[target]
        elif doc["mode"]=="APOCALYPTIC" and target=="electricadvantage:photovoltaic_generator@0":
            excluded[target]="Explicit legacy Apocalyptic restriction"
        elif target not in known: blocked.append(target)
    return {"reachable":known,"blocked":sorted(blocked),"excluded":excluded,"rounds":rounds,
            "unverifiedNativeRecipes":[{"id":r["id"],"output":r.get("output"),"reason":r.get("reason")}
                                      for r in doc["crafting"] if r.get("owned") and r.get("status")!="PASS"],
            "scope":"AVAILABLE_TYPES_FROM_CONCRETE_NATIVE_RECIPE_WITNESSES; NOT_FINITE_MATERIAL_CONSERVATION",
            "machineProduction":"NOT_TESTED",
            "practicalGaps":["Bucket/world-fluid collection is not credited as a free input",
                             "Refinery and other non-registry machine paths require operating evidence",
                             "Apocalyptic parts require actual sampled loot; generated ALT config is not loot proof",
                             "Only the concrete ingredient alternatives actually exercised are credited",
                             "Recipe-book discovery, shift-crafting and player GUI interactions are outside this audit"]}


def canonical(doc):
    value={k:doc[k] for k in ("minecraft","mode","rubber","crafting","targets","ores","smelting","crushing","sampledNativeLoot")}
    for key in ("crafting","targets","smelting","crushing","sampledNativeLoot"):
        value[key]=sorted(value[key],key=lambda x:json.dumps(x,sort_keys=True))
    return value


def run(args):
    root=output_path(args.output)
    target=pinned_profile(args.target_manifest.resolve(),"target112")
    for protected in (args.target_manifest,target[0],*target[2]): require_disjoint(root,protected)
    code=read_json(args.target_manifest)["target112"].get("serverCode")
    if not code: raise AssertionError("Recipe audit requires an exact native engine-code inventory")
    verify_code(target[0],code)
    with socket.socket() as check: check.bind(("127.0.0.1",args.port))
    root.mkdir(parents=True)
    helpers=[Path(__file__),*[Path(__file__).with_name(name) for name in
             ("rig_transport.py","native112.py","native112_code.py","native112_lifecycle.py","native112_diagnostics.py")],
             Path(__file__).parents[1]/"src/main/java/zone/moddev/mc/advantageworks/NativeRecipeProgressionProbe.java"]
    seals={str(p):digest(p) for p in helpers}
    for path in helpers:
        destination=root/"protocol-sources"/path.name
        destination.parent.mkdir(exist_ok=True)
        shutil.copy2(path,destination)
    report={"status":"RUNNING","auditStatus":"RUNNING","completeProgression":"PARTIAL","startedAtUtc":datetime.now(timezone.utc).isoformat(),"modes":{},
            "scope":"BOUNDED_NATIVE_CRAFTING_AND_TYPE_AVAILABILITY_AUDIT","machineProduction":"NOT_TESTED",
            "manifest":str(args.target_manifest.resolve()),"manifestSha256":digest(args.target_manifest),"protocolSha256":seals}
    server=None
    try:
        for mode in ([args.mode] if args.mode else MODES):
            runtime=root/mode.lower()
            launcher=prepare(runtime,target,[],args.port)
            verify_code(runtime,code); write_json(runtime/INVENTORY,code)
            (runtime/"advantage-legacy-recipes.allow").touch()
            config=runtime/"config/poweradvantage.cfg"; config.parent.mkdir(exist_ok=True)
            config.write_text('options {\n S:recipe_mode='+mode+'\n B:plastic_equals_rubber='+str(not args.no_rubber).lower()+'\n}\n',encoding="utf-8")
            proofs={}
            for phase in ("initial","restart"):
                verify_staged(runtime,target)
                label=mode.lower()+"-"+phase
                server=Server(runtime,args.java8,launcher,label)
                server.send("advlegacyrecipes "+label)
                server.wait("ADVLEGACYRECIPES COMPLETE "+label,180)
                doc=read_json(runtime/"advantage-works-results"/("legacy-recipes-"+label+".json"))
                server.send("save-all flush"); server.close(); server=None
                require_native_boot(runtime/("console-"+label+".log")); verify_staged(runtime,target)
                audit_error=None
                try:
                    mode_checks(doc,mode,not args.no_rubber)
                    if any(row.get("owned") and row.get("status")!="PASS" for row in doc["crafting"]):
                        raise AssertionError("Some active owned recipes lack passing native craft/consumption evidence")
                except AssertionError as error: audit_error=str(error)
                proofs[phase]={"native":doc,"availability":reachability(doc),
                               "auditStatus":"FAIL" if audit_error else "PASS","auditError":audit_error}
            if canonical(proofs["initial"]["native"])!=canonical(proofs["restart"]["native"]):
                raise AssertionError("Registered recipe/provider semantics changed across restart")
            report["modes"][mode]=proofs
        # Never label this narrower recipe audit as complete machine qualification.
        report["auditStatus"]="FAIL" if any(phase["auditStatus"]!="PASS" for mode in report["modes"].values() for phase in mode.values()) else "PASS"
        report["status"]="FAIL" if report["auditStatus"]=="FAIL" else "PARTIAL"
    except Exception as error:
        report.update(status="FAIL",auditStatus="FAIL",failure=str(error),traceback=traceback.format_exc())
    finally:
        if server:
            try: server.close()
            except Exception as error: report.update(status="FAIL",shutdownError=str(error))
        report["protocolUnchanged"]=seals=={str(p):digest(p) for p in helpers}
        report["manifestUnchanged"]=report["manifestSha256"]==digest(args.target_manifest)
        if not report["protocolUnchanged"] or not report["manifestUnchanged"]: report.update(status="FAIL",auditStatus="FAIL")
        report["completedAtUtc"]=datetime.now(timezone.utc).isoformat()
        write_json(root/"native112-recipe-audit-results.json",report)
    print("LEGACY_RECIPE_AUDIT auditStatus="+report["auditStatus"]+" completeProgression="+report["completeProgression"],flush=True)
    return 1 if report["status"]=="FAIL" else 2 if report["status"]=="PARTIAL" else 0


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target-manifest",type=Path,required=True)
    parser.add_argument("--java8",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--port",type=int,default=25597)
    parser.add_argument("--mode",choices=MODES)
    parser.add_argument("--no-rubber",action="store_true")
    raise SystemExit(run(parser.parse_args()))
