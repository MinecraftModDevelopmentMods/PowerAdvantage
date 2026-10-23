var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');

function initializeCoreMod() {
    return {
        'poweradvantage_material_tags': {
            'target': { 'type': 'CLASS', 'name': 'net.minecraft.tags.NetworkTagManager' },
            'transformer': function(classNode) {
                var patched = 0;
                for (var i = 0; i < classNode.methods.size(); ++i) {
                    var method = classNode.methods.get(i);
                    if (method.desc !== '(Lnet/minecraft/resources/IResourceManager;)V') continue;
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        if (instruction.getOpcode() !== Opcodes.RETURN) continue;
                        // Forge 25 has no tags-loaded event. This runs after tag resolution,
                        // but before the following recipe and advancement reload listeners.
                        method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                'zone/moddev/mc/poweradvantage/recipe/MaterialTags', 'finishReload', '()V', false));
                        patched++;
                    }
                }
                if (patched !== 1) throw new Error('Power Advantage could not locate the Forge 25 tag reload boundary');
                return classNode;
            }
        }
    };
}
