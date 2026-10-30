package com.mcmoddev.poweradvantage.conduitnetwork;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cyano.poweradvantage.api.ConduitType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.junit.Test;
import com.mcmoddev.poweradvantage.math.BlockPos4D;

public class ConduitNetworkManagerTest {
    private final ConduitNetworkManager manager = new ConduitNetworkManager(new ConduitType("test"));

    private static BlockPos4D pos(int dimension, int x, int z) {
        return new BlockPos4D(dimension, new BlockPos(x, 80, z));
    }

    @Test public void unloadingAnIntermediateChunkInvalidatesTheWholeNetwork() {
        BlockPos4D source = pos(0, 13, 8), bridge = pos(0, 20, 8), sink = pos(0, 40, 8);
        manager.addBlockToNetwork(source, bridge);
        manager.addBlockToNetwork(source, sink);
        manager.invalidateChunk(0, new ChunkPos(1, 0));
        assertFalse(manager.isValidatedNetwork(source));
        assertFalse(manager.isValidatedNetwork(bridge));
        assertFalse(manager.isValidatedNetwork(sink));
    }

    @Test public void loadingAPipeOnlyChunkInvalidatesBothBoundaryNetworks() {
        BlockPos4D left = pos(0, 15, 8), right = pos(0, 32, 8);
        manager.createNewNetwork(left);
        manager.createNewNetwork(right);
        manager.invalidateChunk(0, new ChunkPos(1, 0));
        assertFalse(manager.isValidatedNetwork(left));
        assertFalse(manager.isValidatedNetwork(right));
    }

    @Test public void allFourChunkBoundariesAreInvalidated() {
        BlockPos4D[] edges = {pos(0, 15, 20), pos(0, 32, 20), pos(0, 20, 15), pos(0, 20, 32)};
        for (BlockPos4D edge : edges) manager.createNewNetwork(edge);
        manager.invalidateChunk(0, new ChunkPos(1, 1));
        for (BlockPos4D edge : edges) assertFalse(manager.isValidatedNetwork(edge));
    }

    @Test public void negativeChunkCoordinatesUseTheSameBoundaryRules() {
        BlockPos4D edge = pos(0, -17, -8);
        manager.createNewNetwork(edge);
        manager.invalidateChunk(0, new ChunkPos(-1, -1));
        assertFalse(manager.isValidatedNetwork(edge));
    }

    @Test public void unrelatedNetworksAndDimensionsRemainCached() {
        BlockPos4D nearby = pos(0, 14, 8), distant = pos(0, 80, 8), otherDimension = pos(-1, 20, 8);
        manager.createNewNetwork(nearby);
        manager.createNewNetwork(distant);
        manager.createNewNetwork(otherDimension);
        manager.invalidateChunk(0, new ChunkPos(1, 0));
        assertTrue(manager.isValidatedNetwork(nearby));
        assertTrue(manager.isValidatedNetwork(distant));
        assertTrue(manager.isValidatedNetwork(otherDimension));
    }
}
