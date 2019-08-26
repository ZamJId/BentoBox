package world.bentobox.bentobox.util;

import java.util.Arrays;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.Reason;
import world.bentobox.bentobox.database.objects.IslandDeletion;

/**
 * Deletes islands chunk by chunk
 *
 * @author tastybento
 */
public class DeleteIslandChunks {

    /**
     * This is how many chunks per world will be done in one tick.
     */
    private static final int SPEED = 5;
    private int chunkX;
    private int chunkZ;
    private BukkitTask task;
    private IslandDeletion di;

    public DeleteIslandChunks(BentoBox plugin, IslandDeletion di) {
        // Fire event
        IslandEvent.builder().deletedIslandInfo(di).reason(Reason.DELETE_CHUNKS).build();
        this.chunkX = di.getMinXChunk();
        this.chunkZ = di.getMinZChunk();
        this.di = di;
        // Run through all chunks of the islands and regenerate them.
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i < SPEED; i++) {
                plugin.getIWM().getAddon(di.getWorld()).ifPresent(gm -> {
                    Chunk chunk = di.getWorld().getChunkAt(chunkX, chunkZ);
                    regenerateChunk(gm, chunk);

                    if (plugin.getIWM().isNetherGenerate(di.getWorld()) && plugin.getIWM().isNetherIslands(di.getWorld())) {
                        regenerateChunk(gm, plugin.getIWM().getNetherWorld(di.getWorld()).getChunkAt(chunkX, chunkZ));
                    }
                    if (plugin.getIWM().isEndGenerate(di.getWorld()) && plugin.getIWM().isEndIslands(di.getWorld())) {
                        regenerateChunk(gm, plugin.getIWM().getEndWorld(di.getWorld()).getChunkAt(chunkX, chunkZ));
                    }
                    chunkZ++;
                    if (chunkZ > di.getMaxZChunk()) {
                        chunkZ = di.getMinZChunk();
                        chunkX++;
                        if (chunkX > di.getMaxXChunk()) {
                            // We're done
                            task.cancel();
                            // Fire event
                            IslandEvent.builder().deletedIslandInfo(di).reason(Reason.DELETED).build();
                        }
                    }
                });
            }
        }, 0L, 1L);
    }

    private void regenerateChunk(GameModeAddon gm, Chunk chunk) {
        boolean isLoaded = chunk.isLoaded();
        // Clear all inventories
        Arrays.stream(chunk.getTileEntities()).filter(te -> (te instanceof InventoryHolder))
        .filter(te -> di.inBounds(te.getLocation().getBlockX(), te.getLocation().getBlockZ()))
        .forEach(te -> ((InventoryHolder)te).getInventory().clear());
        // Reset blocks
        MyBiomeGrid grid = new MyBiomeGrid(chunk.getWorld().getEnvironment());
        ChunkGenerator cg = gm.getDefaultWorldGenerator(chunk.getWorld().getName(), "");
        // Will be null if use-own-generator is set to true
        if (cg != null) {
            ChunkData cd = cg.generateChunkData(chunk.getWorld(), new Random(), chunk.getX(), chunk.getZ(), grid);
            int baseX = chunk.getX() << 4;
            int baseZ = chunk.getZ() << 4;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (di.inBounds(baseX + x, baseZ + z)) {
                        chunk.getBlock(x, 0, z).setBiome(grid.getBiome(x, z));
                        for (int y = 0; y < chunk.getWorld().getMaxHeight(); y++) {
                            chunk.getBlock(x, y, z).setBlockData(cd.getBlockData(x, y, z), false);
                        }
                    }
                }
            }
        }
        // Remove all entities in chunk, including any dropped items as a result of clearing the blocks above
        Arrays.stream(chunk.getEntities()).filter(e -> !(e instanceof Player) && di.inBounds(e.getLocation().getBlockX(), e.getLocation().getBlockZ())).forEach(Entity::remove);
        if (!isLoaded) {
            chunk.unload(true);
        }
    }
}
