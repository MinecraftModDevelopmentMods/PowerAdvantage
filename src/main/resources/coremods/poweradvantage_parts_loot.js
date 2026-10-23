var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');

function initializeCoreMod() {
    return {
        'poweradvantage_parts_loot': {
            'target': { 'type': 'CLASS', 'name': 'net.minecraft.world.storage.loot.LootTableManager' },
            'transformer': function(classNode) {
                var patched = 0;
                for (var i = 0; i < classNode.methods.size(); ++i) {
                    var method = classNode.methods.get(i);
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        if (instruction instanceof MethodInsnNode && instruction.owner === 'net/minecraftforge/common/ForgeHooks'
                                && instruction.name === 'loadLootTable') {
                            instruction.owner = 'zone/moddev/mc/poweradvantage/recipe/PartsLoot';
                            patched++;
                        }
                    }
                }
                if (patched !== 1) throw new Error('Power Advantage could not locate the Forge 25 loot loading bridge');
                return classNode;
            }
        }
    };
}
