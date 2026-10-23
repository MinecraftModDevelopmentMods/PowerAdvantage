var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');

function initializeCoreMod() {
    return {
        'poweradvantage_oil_renderer': {
            'target': { 'type': 'CLASS', 'name': 'net.minecraft.client.renderer.BlockFluidRenderer' },
            'transformer': function(classNode) {
                var descriptor = '(Lnet/minecraft/world/IWorldReader;Lnet/minecraft/util/math/BlockPos;' +
                        'Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/fluid/IFluidState;)Z';
                var patched = false;
                for (var i = 0; i < classNode.methods.size(); ++i) {
                    var method = classNode.methods.get(i);
                    // The descriptor also matches the production SRG-named method.
                    if (method.desc !== descriptor) continue;
                    var flag = false, sprites = false, color = false;
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        var hookName = null, hookDescriptor = null;
                        if (!flag && instruction.getOpcode() === Opcodes.ISTORE && instruction.var === 5) {
                            hookName = 'useOilPath';
                            hookDescriptor = '(Lnet/minecraft/fluid/IFluidState;Z)Z';
                            flag = true;
                        } else if (!sprites && instruction.getOpcode() === Opcodes.ASTORE && instruction.var === 6) {
                            hookName = 'overrideSprites';
                            hookDescriptor = '(Lnet/minecraft/fluid/IFluidState;[Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)' +
                                    '[Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;';
                            sprites = true;
                        } else if (!color && instruction.getOpcode() === Opcodes.ISTORE && instruction.var === 7) {
                            hookName = 'overrideColor';
                            hookDescriptor = '(Lnet/minecraft/fluid/IFluidState;I)I';
                            color = true;
                        }
                        if (hookName === null) continue;
                        var reference = instruction.var === 6;
                        var hook = new InsnList();
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 4));
                        hook.add(new VarInsnNode(reference ? Opcodes.ALOAD : Opcodes.ILOAD, instruction.var));
                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                                'zone/moddev/mc/poweradvantage/client/ClientOilRenderer',
                                hookName, hookDescriptor, false));
                        hook.add(new VarInsnNode(reference ? Opcodes.ASTORE : Opcodes.ISTORE, instruction.var));
                        method.instructions.insert(instruction, hook);
                    }
                    if (!flag || !sprites || !color) throw new Error('Power Advantage could not patch the Forge 25 fluid renderer');
                    patched = true;
                }
                if (!patched) throw new Error('Power Advantage could not find the Forge 25 fluid renderer');
                return classNode;
            }
        }
    };
}
