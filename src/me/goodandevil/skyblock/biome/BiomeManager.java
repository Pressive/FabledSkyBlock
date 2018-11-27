package me.goodandevil.skyblock.biome;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import me.goodandevil.skyblock.SkyBlock;
import me.goodandevil.skyblock.config.FileManager.Config;
import me.goodandevil.skyblock.island.Island;
import me.goodandevil.skyblock.island.IslandManager;
import me.goodandevil.skyblock.playerdata.PlayerData;
import me.goodandevil.skyblock.playerdata.PlayerDataManager;
import me.goodandevil.skyblock.utils.version.NMSUtil;
import me.goodandevil.skyblock.utils.world.LocationUtil;

public class BiomeManager {
	
	private final SkyBlock skyblock;
	private Map<UUID, me.goodandevil.skyblock.biome.Biome> playerBiomeStorage = new HashMap<>();

	public BiomeManager(SkyBlock skyblock) {
		this.skyblock = skyblock;
		
		new BiomeTask(skyblock).runTaskTimerAsynchronously(skyblock, 0L, 20L);
		
		for (Player all : Bukkit.getOnlinePlayers()) {
			loadPlayer(all);
		}
	}
	
	public void onDisable() {
		for (Player all : Bukkit.getOnlinePlayers()) {
			savePlayer(all);
		}
	}
	
	public void setBiome(Player player, Island island, Biome biome) {
	    new BukkitRunnable() {
	    	@Override
			public void run() {
	    		List<Chunk> chunks = new ArrayList<>();
	    		
	    		Location location = island.getLocation(me.goodandevil.skyblock.island.Location.World.Normal, me.goodandevil.skyblock.island.Location.Environment.Island);
	    		
	    		for (Location locationList : LocationUtil.getLocations(new Location(location.getWorld(), location.getBlockX() - island.getRadius(), 0, location.getBlockZ() - island.getRadius()), new Location(location.getWorld(), location.getBlockX() + island.getRadius(), location.getWorld().getMaxHeight(), location.getBlockZ() + island.getRadius()))) {
	            	Block block = locationList.getBlock();
	            	boolean containsChunk = false;
            		
            		for (Chunk chunkList : chunks) {
            			if (chunkList.getX() == block.getChunk().getX() && chunkList.getZ() == block.getChunk().getZ()) {
            				containsChunk = true;
            				break;
            			}
            		}
            		
            		if (!containsChunk) {
            			chunks.add(block.getChunk());
            		}
	    		}
	    		
	    		for (Chunk chunkList : chunks) {
		    		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(skyblock, new Runnable() {
						@Override
						public void run() {
							location.getWorld().setBiome(chunkList.getX(), chunkList.getZ(), biome);
						}
		    		});
	    		}
	    		
	    	    if (player != null) {
	    	    	PlayerDataManager playerDataManager = skyblock.getPlayerDataManager();
	    	    	IslandManager islandManager = skyblock.getIslandManager();
	    	    	
	    	    	if (playerDataManager.hasPlayerData(player)) {
	    	    		PlayerData playerData = playerDataManager.getPlayerData(player);
	    	    		
	    	    		if (playerData.getOwner() != null) {
	    	    	    	Island island = islandManager.getIsland(playerData.getOwner());
	    	    	    	updateBiome(island, chunks);
	    	    		}
	    	    	}
	    	    }
	    	}
	    }.runTaskAsynchronously(skyblock);
	}
	
	public void updateBiome(Island island, List<Chunk> chunks) {
		Class<?> packetPlayOutMapChunkClass = NMSUtil.getNMSClass("PacketPlayOutMapChunk");
		Class<?> chunkClass = NMSUtil.getNMSClass("Chunk");
		
    	for (Player all : skyblock.getIslandManager().getPlayersAtIsland(island, me.goodandevil.skyblock.island.Location.World.Normal)) {
    	    for (Chunk chunkList : chunks) {
    			try {
    				if (NMSUtil.getVersionNumber() < 10) {
    					NMSUtil.sendPacket(all, packetPlayOutMapChunkClass.getConstructor(chunkClass, boolean.class, int.class).newInstance(all.getLocation().getChunk().getClass().getMethod("getHandle").invoke(chunkList), true, 20));
    				} else {
    					NMSUtil.sendPacket(all, packetPlayOutMapChunkClass.getConstructor(chunkClass, int.class).newInstance(all.getLocation().getChunk().getClass().getMethod("getHandle").invoke(chunkList), 65535));
    				}
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | NoSuchMethodException | SecurityException e) {
					e.printStackTrace();
				}
    	    }
    	}
	}
	
	public void createPlayer(Player player, int time) {
		Config config = skyblock.getFileManager().getConfig(new File(new File(skyblock.getDataFolder().toString() + "/player-data"), player.getUniqueId().toString() + ".yml"));
		File configFile = config.getFile();
		FileConfiguration configLoad = config.getFileConfiguration();
		
		configLoad.set("Island.Biome.Cooldown", time);
		
		try {
			configLoad.save(configFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		me.goodandevil.skyblock.biome.Biome biome = new me.goodandevil.skyblock.biome.Biome(time);
		playerBiomeStorage.put(player.getUniqueId(), biome);
	}
	
	public void loadPlayer(Player player) {
		Config config = skyblock.getFileManager().getConfig(new File(new File(skyblock.getDataFolder().toString() + "/player-data"), player.getUniqueId().toString() + ".yml"));
		FileConfiguration configLoad = config.getFileConfiguration();
		
		if (configLoad.getString("Island.Biome.Cooldown") != null) {
			me.goodandevil.skyblock.biome.Biome biome = new me.goodandevil.skyblock.biome.Biome(configLoad.getInt("Island.Biome.Cooldown"));
			playerBiomeStorage.put(player.getUniqueId(), biome);
		}
	}
	
	public void removePlayer(Player player) {
		if (hasPlayer(player)) {
			Config config = skyblock.getFileManager().getConfig(new File(new File(skyblock.getDataFolder().toString() + "/player-data"), player.getUniqueId().toString() + ".yml"));
			File configFile = config.getFile();
			FileConfiguration configLoad = config.getFileConfiguration();
			
			configLoad.set("Island.Biome.Cooldown", null);
			
			try {
				configLoad.save(configFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			playerBiomeStorage.remove(player.getUniqueId());
		}
	}
	
	public void savePlayer(Player player) {
		if (hasPlayer(player)) {
			me.goodandevil.skyblock.biome.Biome biome = getPlayer(player);
			
			Config config = skyblock.getFileManager().getConfig(new File(new File(skyblock.getDataFolder().toString() + "/player-data"), player.getUniqueId().toString() + ".yml"));
			File configFile = config.getFile();
			FileConfiguration configLoad = config.getFileConfiguration();
			
			configLoad.set("Island.Biome.Cooldown", biome.getTime());
			
			try {
				configLoad.save(configFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void unloadPlayer(Player player) {
		if (hasPlayer(player)) {
			playerBiomeStorage.remove(player.getUniqueId());
		}
	}

	public me.goodandevil.skyblock.biome.Biome getPlayer(Player player) {
		if (hasPlayer(player)) {
			return playerBiomeStorage.get(player.getUniqueId());
		}
		
		return null;
	}
	
	public boolean hasPlayer(Player player) {
		return playerBiomeStorage.containsKey(player.getUniqueId());
	}
}
