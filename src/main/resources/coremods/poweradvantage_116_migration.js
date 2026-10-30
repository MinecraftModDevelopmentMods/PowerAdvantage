var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');

// Only the pre-datafix migration needs bytecode integration on Forge 1.16.
var hook = 'zone/moddev/mc/poweradvantage/migration/LegacyWorldDataHook';

function patchPlayerDataFix(classNode) {
    var descriptor = '(Lcom/mojang/datafixers/DataFixer;' +
            'Lnet/minecraft/util/datafix/DefaultTypeReferences;' +
            'Lnet/minecraft/nbt/CompoundNBT;I)Lnet/minecraft/nbt/CompoundNBT;';
    var matches = 0;
    var matchedMethod = null;
    var matchedCall = null;
    for (var i = 0; i < classNode.methods.size(); ++i) {
        var method = classNode.methods.get(i);
        for (var instruction = method.instructions.getFirst(); instruction !== null;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() === Opcodes.INVOKESTATIC &&
                    instruction.owner === 'net/minecraft/nbt/NBTUtil' && instruction.desc === descriptor) {
                ++matches;
                matchedMethod = method;
                matchedCall = instruction;
            }
        }
    }
    if (matches !== 1) {
        throw new Error('Power Advantage requires exactly one native player datafix call in ' + classNode.name);
    }
    matchedMethod.instructions.set(matchedCall, new MethodInsnNode(Opcodes.INVOKESTATIC,
            'zone/moddev/mc/poweradvantage/migration/LegacyPlayerDataFixes', 'update', descriptor, false));
    return classNode;
}

function initializeCoreMod() {
    return {
        'poweradvantage_legacy_player_file': {
            'target': { 'type': 'CLASS', 'name': 'net.minecraft.world.storage.PlayerData' },
            'transformer': patchPlayerDataFix
        },
        'poweradvantage_legacy_embedded_player': {
            'target': { 'type': 'CLASS', 'name': 'net.minecraft.world.storage.ServerWorldInfo' },
            'transformer': patchPlayerDataFix
        },
        'poweradvantage_legacy_world_info': {
            'target': { 'type': 'CLASS', 'name': 'net.minecraft.world.storage.SaveFormat' },
            'transformer': function(classNode) {
                var patched = false;
                for (var i = 0; i < classNode.methods.size(); ++i) {
                    var method = classNode.methods.get(i);
                    if (method.desc !== '(Lcom/mojang/serialization/DynamicOps;' +
                            'Lnet/minecraft/util/datafix/codec/DatapackCodec;' +
                            'Lnet/minecraft/world/storage/SaveFormat$LevelSave;' +
                            'Ljava/io/File;Lcom/mojang/datafixers/DataFixer;)' +
                            'Lnet/minecraft/world/storage/ServerWorldInfo;') continue;
                    var prefix = new InsnList();
                    prefix.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, hook,
                            'prepareLegacyWorld', '(Ljava/io/File;)V', false));
                    method.instructions.insert(prefix);
                    patched = true;
                }
                if (!patched) throw new Error('Power Advantage could not attach its legacy world reader');
                return classNode;
            }
        },
        'poweradvantage_legacy_chunk_status': {
            'target': { 'type': 'CLASS', 'name': 'net.minecraft.world.chunk.storage.ChunkLoader' },
            'transformer': function(classNode) {
                var descriptor = '(Lnet/minecraft/util/RegistryKey;' +
                        'Ljava/util/function/Supplier;Lnet/minecraft/nbt/CompoundNBT;)' +
                        'Lnet/minecraft/nbt/CompoundNBT;';
                var patchedRead = false;
                var patchedReturn = false;
                var patchedOwnership = false;
                var patchedEntities = false;
                for (var i = 0; i < classNode.methods.size(); ++i) {
                    var method = classNode.methods.get(i);
                    if (method.desc === '(Lnet/minecraft/util/math/ChunkPos;)Lnet/minecraft/nbt/CompoundNBT;') {
                        for (var read = method.instructions.getFirst(); read !== null; read = read.getNext()) {
                            if (read.getOpcode() === Opcodes.ARETURN) {
                                method.instructions.insertBefore(read, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                        hook, 'copyChunkForMigration',
                                        '(Lnet/minecraft/nbt/CompoundNBT;)Lnet/minecraft/nbt/CompoundNBT;', false));
                                patchedOwnership = true;
                            }
                        }
                    }
                    if (method.desc !== descriptor) continue;
                    // Other coremods prepend their preparation. Stay after all those entry
                    // hooks so modded item durability is rescued before entity flattening.
                    for (var first = method.instructions.getFirst(); first !== null; first = first.getNext()) {
                        if (first.getOpcode() === Opcodes.INVOKESTATIC && first.owner === classNode.name &&
                                first.desc === '(Lnet/minecraft/nbt/CompoundNBT;)I') {
                            var entities = new InsnList();
                            entities.add(new VarInsnNode(Opcodes.ALOAD, 3));
                            entities.add(new MethodInsnNode(Opcodes.INVOKESTATIC, hook,
                                    'prepareLegacyEntities', '(Lnet/minecraft/nbt/CompoundNBT;)V', false));
                            method.instructions.insertBefore(first, entities);
                            patchedEntities = true;
                            break;
                        }
                    }
                    var prepare = new InsnList();
                    prepare.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    prepare.add(new MethodInsnNode(Opcodes.INVOKESTATIC, hook,
                            'prepareLegacyChunk', '(Lnet/minecraft/nbt/CompoundNBT;)V', false));
                    method.instructions.insert(prepare);
                    patchedRead = true;
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        if (instruction.getOpcode() === Opcodes.ARETURN) {
                            method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    hook, 'finalizeLegacyChunk',
                                    '(Lnet/minecraft/nbt/CompoundNBT;)Lnet/minecraft/nbt/CompoundNBT;', false));
                            patchedReturn = true;
                        }
                    }
                }
                if (!patchedRead || !patchedReturn || !patchedOwnership || !patchedEntities) {
                    throw new Error('Power Advantage could not attach its legacy chunk converter');
                }
                return classNode;
            }
        },
        'poweradvantage_legacy_worldgen_guard': {
            'target': { 'type': 'CLASS', 'name': 'net.minecraft.world.gen.WorldGenRegion' },
            'transformer': function(classNode) {
                var descriptor = '(Lnet/minecraft/util/math/BlockPos;' +
                        'Lnet/minecraft/block/BlockState;II)Z';
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
                if (!patched) throw new Error('Power Advantage could not protect existing legacy terrain');
                return classNode;
            }
        }
    };
}
