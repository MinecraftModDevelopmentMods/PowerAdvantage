var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');

var hook = 'zone/moddev/mc/poweradvantage/migration/LegacyWorldDataHook';

function initializeCoreMod() {
    return {
        'poweradvantage_legacy_world_info': {
            'target': { 'type': 'CLASS', 'name': 'net.minecraft.world.storage.SaveFormatOld' },
            'transformer': function(classNode) {
                var patched = false;
                for (var i = 0; i < classNode.methods.size(); ++i) {
                    var method = classNode.methods.get(i);
                    if (method.desc !== '(Ljava/io/File;Lcom/mojang/datafixers/DataFixer;' +
                            'Lnet/minecraft/world/storage/SaveHandler;)Lnet/minecraft/world/storage/WorldInfo;') continue;
                    var prefix = new InsnList();
                    prefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, hook,
                            'prepareLegacyWorld', '(Ljava/io/File;)V', false));
                    method.instructions.insert(prefix);
                    patched = true;
                }
                if (!patched) throw new Error('Power Advantage could not patch the Forge 25 legacy world reader');
                return classNode;
            }
        },
        'poweradvantage_legacy_chunk_status': {
            'target': { 'type': 'CLASS', 'name': 'net.minecraft.world.chunk.storage.AnvilChunkLoader' },
            'transformer': function(classNode) {
                var descriptor = '(Lnet/minecraft/world/dimension/DimensionType;' +
                        'Lnet/minecraft/world/storage/WorldSavedDataStorage;II)' +
                        'Lnet/minecraft/nbt/NBTTagCompound;';
                var patchedRead = false;
                var patchedReturn = false;
                for (var i = 0; i < classNode.methods.size(); ++i) {
                    var method = classNode.methods.get(i);
                    if (method.desc !== descriptor) continue;
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        var previous = instruction.getPrevious();
                        if (instruction.getOpcode() === Opcodes.ASTORE && previous !== null
                                && previous.getOpcode() === Opcodes.INVOKESTATIC
                                && previous.owner === 'net/minecraft/nbt/CompressedStreamTools'
                                && previous.desc.endsWith(')Lnet/minecraft/nbt/NBTTagCompound;')) {
                            var prepare = new InsnList();
                            prepare.add(new VarInsnNode(Opcodes.ALOAD, instruction.var));
                            prepare.add(new MethodInsnNode(Opcodes.INVOKESTATIC, hook,
                                    'prepareLegacyChunk', '(Lnet/minecraft/nbt/NBTTagCompound;)V', false));
                            method.instructions.insert(instruction, prepare);
                            patchedRead = true;
                        } else if (instruction.getOpcode() === Opcodes.ARETURN) {
                            method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    hook, 'finalizeLegacyChunk',
                                    '(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;', false));
                            patchedReturn = true;
                        }
                    }
                }
                if (!patchedRead || !patchedReturn) {
                    throw new Error('Power Advantage could not patch the Forge 25 legacy chunk loader');
                }
                return classNode;
            }
        },
        'poweradvantage_legacy_worldgen_guard': {
            'target': { 'type': 'CLASS', 'name': 'net.minecraft.world.gen.WorldGenRegion' },
            'transformer': function(classNode) {
                var descriptor = '(Lnet/minecraft/util/math/BlockPos;' +
                        'Lnet/minecraft/block/state/IBlockState;I)Z';
                var patched = false;
                for (var i = 0; i < classNode.methods.size(); ++i) {
                    var method = classNode.methods.get(i);
                    if (method.desc !== descriptor) continue;
                    var allowed = new LabelNode();
                    var prefix = new InsnList();
                    prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, hook,
                            'shouldBlockWorldgenWrite', '(Lnet/minecraft/util/math/BlockPos;)Z', false));
                    prefix.add(new JumpInsnNode(Opcodes.IFEQ, allowed));
                    prefix.add(new InsnNode(Opcodes.ICONST_0));
                    prefix.add(new InsnNode(Opcodes.IRETURN));
                    prefix.add(allowed);
                    method.instructions.insert(prefix);
                    patched = true;
                }
                if (!patched) throw new Error('Power Advantage could not patch the Forge 25 legacy-world write guard');
                return classNode;
            }
        }
    };
}
