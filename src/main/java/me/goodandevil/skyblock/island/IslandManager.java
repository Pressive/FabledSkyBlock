package me.goodandevil.skyblock.island;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import me.goodandevil.skyblock.SkyBlock;
import me.goodandevil.skyblock.ban.BanManager;
import me.goodandevil.skyblock.config.FileManager;
import me.goodandevil.skyblock.config.FileManager.Config;
import me.goodandevil.skyblock.events.IslandCreateEvent;
import me.goodandevil.skyblock.events.IslandDeleteEvent;
import me.goodandevil.skyblock.events.IslandLoadEvent;
import me.goodandevil.skyblock.events.IslandOwnershipTransferEvent;
import me.goodandevil.skyblock.events.IslandUnloadEvent;
import me.goodandevil.skyblock.invite.Invite;
import me.goodandevil.skyblock.invite.InviteManager;
import me.goodandevil.skyblock.island.Location.World;
import me.goodandevil.skyblock.message.MessageManager;
import me.goodandevil.skyblock.playerdata.PlayerData;
import me.goodandevil.skyblock.playerdata.PlayerDataManager;
import me.goodandevil.skyblock.scoreboard.Scoreboard;
import me.goodandevil.skyblock.scoreboard.ScoreboardManager;
import me.goodandevil.skyblock.sound.SoundManager;
import me.goodandevil.skyblock.structure.Structure;
import me.goodandevil.skyblock.upgrade.Upgrade;
import me.goodandevil.skyblock.upgrade.UpgradeManager;
import me.goodandevil.skyblock.utils.OfflinePlayer;
import me.goodandevil.skyblock.utils.structure.StructureUtil;
import me.goodandevil.skyblock.utils.version.Materials;
import me.goodandevil.skyblock.utils.version.NMSUtil;
import me.goodandevil.skyblock.utils.version.Sounds;
import me.goodandevil.skyblock.utils.world.LocationUtil;
import me.goodandevil.skyblock.utils.world.WorldBorder;
import me.goodandevil.skyblock.utils.world.block.BlockDegreesType;
import me.goodandevil.skyblock.visit.VisitManager;
import me.goodandevil.skyblock.world.WorldManager;

public class IslandManager {
	
	private final SkyBlock skyblock;
	
	private double x = 0, offset = 1200;
	
	private List<IslandLocation> islandLocations = new ArrayList<>();
	private Map<UUID, Island> islandStorage = new HashMap<>();
	
	public IslandManager(SkyBlock skyblock) {
		this.skyblock = skyblock;
		
		Config config = skyblock.getFileManager().getConfig(new File(skyblock.getDataFolder(), "config.yml"));
		FileConfiguration configLoad = config.getFileConfiguration();
		
		for (Location.World worldList : Location.World.values()) {
			ConfigurationSection configSection = configLoad.getConfigurationSection("World." + worldList.name() + ".nextAvailableLocation");
			islandLocations.add(new IslandLocation(worldList, configSection.getDouble("x"), configSection.getDouble("z")));
		}
		
		for (Player all : Bukkit.getOnlinePlayers()) {
			loadIsland(all.getUniqueId());
		}
	}

	public void onDisable() {
		for (int i = 0; i < islandStorage.size(); i++) {
			UUID islandOwnerUUID = (UUID) islandStorage.keySet().toArray()[i];
			Island island = islandStorage.get(islandOwnerUUID);
			island.save();
		}
	}
	
	public void saveNextAvailableLocation() {
		FileManager fileManager = skyblock.getFileManager();
		Config config = fileManager.getConfig(new File(skyblock.getDataFolder(), "config.yml"));
		
		File configFile = config.getFile();
		FileConfiguration configLoad = config.getFileConfiguration();
		
		for (Location.World worldList : Location.World.values()) {
			for (IslandLocation islandLocationList : islandLocations) {
				if (islandLocationList.getWorld() == worldList) {
					ConfigurationSection configSection = configLoad.createSection("World." + worldList.name() + ".nextAvailableLocation");
					configSection.set("x", islandLocationList.getX());
					configSection.set("z", islandLocationList.getZ());
				}
			}
		}
		
		fileManager.saveConfig(configLoad.saveToString(), configFile);
	}
	
	public void setNextAvailableLocation(Location.World world, org.bukkit.Location location) {
		for (IslandLocation islandLocationList : islandLocations) {
			if (islandLocationList.getWorld() == world) {
				islandLocationList.setX(location.getX());
				islandLocationList.setZ(location.getZ());
			}
		}
	}
	
	public org.bukkit.Location prepareNextAvailableLocation(Location.World world) {
		for (IslandLocation islandLocationList : islandLocations) {
			if (islandLocationList.getWorld() == world) {
				double x = islandLocationList.getX() + offset, z = islandLocationList.getZ();
				
				if (x > Math.abs(this.x)) {
					z += offset;
					islandLocationList.setX(this.x);
					x = islandLocationList.getX() + offset;
					islandLocationList.setZ(z);
				}
				
				return new org.bukkit.Location(skyblock.getWorldManager().getWorld(world), x, 72, z);
			}
		}
		
		return null;
	}
	
	public void createIsland(Player player, Structure structure) {
		ScoreboardManager scoreboardManager = skyblock.getScoreboardManager();
		FileManager fileManager = skyblock.getFileManager();
		
		if (fileManager.getConfig(new File(skyblock.getDataFolder(), "locations.yml")).getFileConfiguration().getString("Location.Spawn") == null) {
			skyblock.getMessageManager().sendMessage(player, fileManager.getConfig(new File(skyblock.getDataFolder(), "language.yml")).getFileConfiguration().getString("Island.Creator.Error.Message"));
			skyblock.getSoundManager().playSound(player, Sounds.ANVIL_LAND.bukkitSound(), 1.0F, 1.0F);
			
			return;
		}
		
		Island island = new Island(player.getUniqueId(), prepareNextAvailableLocation(Location.World.Normal), prepareNextAvailableLocation(Location.World.Nether));
		islandStorage.put(player.getUniqueId(), island);
		
		try {
			File structureFile = new File(new File(skyblock.getDataFolder().toString() + "/structures"), structure.getFile());
			
			for (World worldList : World.values()) {
				Float[] direction = StructureUtil.pasteStructure(StructureUtil.loadStructure(structureFile), island.getLocation(worldList, Location.Environment.Island), BlockDegreesType.ROTATE_360);
				org.bukkit.Location spawnLocation = island.getLocation(worldList, Location.Environment.Main).clone();
				spawnLocation.setYaw(direction[0]);
				spawnLocation.setPitch(direction[1]);
				island.setLocation(worldList, Location.Environment.Main, spawnLocation);
				island.setLocation(worldList, Location.Environment.Visitor, spawnLocation);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		Bukkit.getServer().getScheduler().runTaskAsynchronously(skyblock, new Runnable() {
			@Override
			public void run() {
				Config config = fileManager.getConfig(new File(skyblock.getDataFolder(), "config.yml"));
				FileConfiguration configLoad = config.getFileConfiguration();
				
				int minimumSize = configLoad.getInt("Island.Size.Minimum");
				int maximumSize = configLoad.getInt("Island.Size.Maximum");
				
				if (minimumSize < 0 || minimumSize > 1000) {
					minimumSize = 50;
				}
				
				if (maximumSize < 0 || maximumSize > 1000) {
					maximumSize = 100;
				}
				
				for (int i = maximumSize; i > minimumSize; i--) {
					if (player.hasPermission("skyblock.size." + i) || player.hasPermission("skyblock.*")) {
						island.setSize(i);
						
						break;
					}
				}
			}
		});
		
		Config config = fileManager.getConfig(new File(skyblock.getDataFolder(), "config.yml"));
		FileConfiguration configLoad = config.getFileConfiguration();
		
		if (configLoad.getBoolean("Island.Creation.Cooldown.Creation.Enable")) {
			skyblock.getCreationManager().createPlayer(player, configLoad.getInt("Island.Creation.Cooldown.Time"));
		}
		
		Bukkit.getServer().getPluginManager().callEvent(new IslandCreateEvent(player, island));
		
		for (Location.World worldList : Location.World.values()) {
			setNextAvailableLocation(worldList, island.getLocation(worldList, Location.Environment.Island));
		}
		
		saveNextAvailableLocation();
		
		skyblock.getPlayerDataManager().getPlayerData(player).setIsland(player.getUniqueId());
		
		config = fileManager.getConfig(new File(skyblock.getDataFolder(), "language.yml"));
		configLoad = config.getFileConfiguration();
		
		if (scoreboardManager != null) {
			Scoreboard scoreboard = scoreboardManager.getScoreboard(player);
			scoreboard.cancel();
			scoreboard.setDisplayName(ChatColor.translateAlternateColorCodes('&', configLoad.getString("Scoreboard.Island.Solo.Displayname")));
			scoreboard.setDisplayList(configLoad.getStringList("Scoreboard.Island.Solo.Empty.Displaylines"));
			scoreboard.run();
		}
		
		Bukkit.getServer().getScheduler().runTask(skyblock, new Runnable() {
			@Override
			public void run() {
				Config config = fileManager.getConfig(new File(skyblock.getDataFolder(), "config.yml"));
				FileConfiguration configLoad = config.getFileConfiguration();
				
				if (configLoad.getString("Island.Creation.Commands.Player") != null) {
					List<String> commands = configLoad.getStringList("Island.Creation.Commands.Player");
					
					if (commands != null) {
						for (String commandList : commands) {
							Bukkit.getServer().dispatchCommand(player, commandList.replace("%player", player.getName()));
						}
					}
				}
				
				if (configLoad.getString("Island.Creation.Commands.Console") != null) {
					List<String> commands = configLoad.getStringList("Island.Creation.Commands.Console");
					
					if (commands != null) {
						for (String commandList : commands) {
							Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), commandList.replace("%player", player.getName()));
						}
					}
				}
				
				player.teleport(island.getLocation(Location.World.Normal, Location.Environment.Main));
			}
		});
		
		if (NMSUtil.getVersionNumber() < 13) {
			Bukkit.getServer().getScheduler().runTaskLater(skyblock, new Runnable() {
				@Override
				public void run() {
					skyblock.getBiomeManager().setBiome(island, Biome.valueOf(fileManager.getConfig(new File(skyblock.getDataFolder(), "config.yml")).getFileConfiguration().getString("Island.Biome.Default.Type").toUpperCase()));
				}
			}, 20L);	
		}
	}
	
	public void giveIslandOwnership(UUID uuid) {
		FileManager fileManager = skyblock.getFileManager();
		PlayerDataManager playerDataManager = skyblock.getPlayerDataManager();
		
		Player targetPlayer = Bukkit.getServer().getPlayer(uuid);
		UUID islandOwnerUUID;
		
		if (targetPlayer == null) {
			OfflinePlayer offlinePlayer = new OfflinePlayer(uuid);
			islandOwnerUUID = offlinePlayer.getOwner();
		} else {
			islandOwnerUUID = playerDataManager.getPlayerData(targetPlayer).getOwner();
		}
		
		if (containsIsland(islandOwnerUUID)) {
			Island island = getIsland(islandOwnerUUID);
			island.getLevel().setOwnerUUID(uuid);
			island.setOwnerUUID(uuid);
			
			Config config = fileManager.getConfig(new File(skyblock.getDataFolder(), "config.yml"));
			FileConfiguration configLoad = config.getFileConfiguration();
			
			if (configLoad.getBoolean("Island.Ownership.Password.Reset")) {
				island.setPassword(null);
			}
			
			File oldCoopDataFile = new File(new File(skyblock.getDataFolder().toString() + "/coop-data"), islandOwnerUUID.toString() + ".yml");
			
			if (fileManager.isFileExist(oldCoopDataFile)) {
				File newCoopDataFile = new File(new File(skyblock.getDataFolder().toString() + "/coop-data"), uuid.toString() + ".yml");
				
				fileManager.unloadConfig(oldCoopDataFile);
				fileManager.unloadConfig(newCoopDataFile);
				oldCoopDataFile.renameTo(newCoopDataFile);	
			}
			
			File oldSettingDataFile = new File(new File(skyblock.getDataFolder().toString() + "/setting-data"), islandOwnerUUID.toString() + ".yml");
			File newSettingDataFile = new File(new File(skyblock.getDataFolder().toString() + "/setting-data"), uuid.toString() + ".yml");
			
			fileManager.unloadConfig(oldSettingDataFile);
			fileManager.unloadConfig(newSettingDataFile);
			oldSettingDataFile.renameTo(newSettingDataFile);
			
			File oldIslandDataFile = new File(new File(skyblock.getDataFolder().toString() + "/island-data"), islandOwnerUUID.toString() + ".yml");
			File newIslandDataFile = new File(new File(skyblock.getDataFolder().toString() + "/island-data"), uuid.toString() + ".yml");
			
			fileManager.unloadConfig(oldIslandDataFile);
			fileManager.unloadConfig(newIslandDataFile);
			oldIslandDataFile.renameTo(newIslandDataFile);
			
			skyblock.getVisitManager().transfer(uuid, islandOwnerUUID);
			skyblock.getBanManager().transfer(uuid, islandOwnerUUID);
			skyblock.getInviteManager().tranfer(uuid, islandOwnerUUID);
			
			if (configLoad.getBoolean("Island.Ownership.Transfer.Operator")) {
				island.setRole(Role.Operator, islandOwnerUUID);
			} else {
				island.setRole(Role.Member, islandOwnerUUID);
			}
			
			if (island.isRole(Role.Member, uuid)) {
				island.removeRole(Role.Member, uuid);
			} else {
				island.removeRole(Role.Operator, uuid);
			}
			
			removeIsland(islandOwnerUUID);
			islandStorage.put(uuid, island);
			
			Bukkit.getServer().getPluginManager().callEvent(new IslandOwnershipTransferEvent(island, islandOwnerUUID, uuid));
			
			ArrayList<UUID> islandMembers = new ArrayList<>();
			islandMembers.addAll(island.getRole(Role.Member));
			islandMembers.addAll(island.getRole(Role.Operator));
			islandMembers.add(uuid);
			
			for (UUID islandMemberList : islandMembers) {
				targetPlayer = Bukkit.getServer().getPlayer(islandMemberList);
				
				if (targetPlayer == null) {
					File configFile = new File(new File(skyblock.getDataFolder().toString() + "/player-data"), islandMemberList.toString() + ".yml");
					configLoad = YamlConfiguration.loadConfiguration(configFile);
					configLoad.set("Island.Owner", uuid.toString());
					
					try {
						configLoad.save(configFile);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					PlayerData playerData = playerDataManager.getPlayerData(targetPlayer);
					playerData.setOwner(uuid);
					playerData.save();
				}
			}
		}
	}

	public void deleteIsland(Island island) {
		PlayerDataManager playerDataManager = skyblock.getPlayerDataManager();
		FileManager fileManager = skyblock.getFileManager();
		
		skyblock.getVisitManager().removeVisitors(island, VisitManager.Removal.Deleted);
		
		FileConfiguration configLoad = fileManager.getConfig(new File(skyblock.getDataFolder(), "config.yml")).getFileConfiguration();
		
		for (Player all : Bukkit.getOnlinePlayers()) {
			if ((island.isRole(Role.Member, all.getUniqueId()) || island.isRole(Role.Operator, all.getUniqueId()) || island.isRole(Role.Owner, all.getUniqueId())) && playerDataManager.hasPlayerData(all)) {
				PlayerData playerData = playerDataManager.getPlayerData(all);
				playerData.setOwner(null);
				playerData.setMemberSince(null);
				playerData.setChat(false);
				playerData.save();
				
				if (configLoad.getBoolean("Island.Creation.Cooldown.Deletion.Enable")) {
					skyblock.getCreationManager().createPlayer(all, configLoad.getInt("Island.Creation.Cooldown.Time"));
				}
			}
			
			InviteManager inviteManager = skyblock.getInviteManager();
			
			if (inviteManager.hasInvite(all.getUniqueId())) {
				Invite invite = inviteManager.getInvite(all.getUniqueId());
				
				if (invite.getOwnerUUID().equals(island.getOwnerUUID())) {
					inviteManager.removeInvite(all.getUniqueId());
				}
			}
		}
		
		fileManager.deleteConfig(new File(new File(skyblock.getDataFolder().toString() + "/coop-data"), island.getOwnerUUID().toString() + ".yml"));
		fileManager.deleteConfig(new File(new File(skyblock.getDataFolder().toString() + "/setting-data"), island.getOwnerUUID().toString() + ".yml"));
		fileManager.deleteConfig(new File(new File(skyblock.getDataFolder().toString() + "/island-data"), island.getOwnerUUID().toString() + ".yml"));
		
		Bukkit.getServer().getPluginManager().callEvent(new IslandDeleteEvent(island));
		
		islandStorage.remove(island.getOwnerUUID());
	}

	public void loadIsland(UUID uuid) {
		WorldManager worldManager = skyblock.getWorldManager();
		FileManager fileManager = skyblock.getFileManager();
		
		UUID islandOwnerUUID = null;
		
		if (isIslandExist(uuid)) {
			islandOwnerUUID = uuid;
		} else {
			Config config = fileManager.getConfig(new File(new File(skyblock.getDataFolder().toString() + "/player-data"), uuid.toString() + ".yml"));
			FileConfiguration configLoad = config.getFileConfiguration();
			
			if (configLoad.getString("Island.Owner") != null) {
				islandOwnerUUID = UUID.fromString(configLoad.getString("Island.Owner"));
			}
		}
		
		if (islandOwnerUUID != null && !hasIsland(islandOwnerUUID)) {
			File configFile = new File(skyblock.getDataFolder().toString() + "/island-data");
			Config config = fileManager.getConfig(new File(configFile, islandOwnerUUID.toString() + ".yml"));
			
			org.bukkit.Location islandNormalLocation = fileManager.getLocation(config, "Location.Normal.Island", true);
			
			if (islandNormalLocation.getWorld() == null) {
				islandNormalLocation.setWorld(worldManager.getWorld(Location.World.Normal));
			}
			
			org.bukkit.Location islandNetherLocation = fileManager.getLocation(config, "Location.Nether.Island", true);
			
			if (islandNetherLocation.getWorld() == null) {
				islandNetherLocation.setWorld(worldManager.getWorld(Location.World.Nether));
			}
			
			Island island = new Island(islandOwnerUUID, new org.bukkit.Location(islandNormalLocation.getWorld(), islandNormalLocation.getBlockX(), 72, islandNormalLocation.getBlockZ()), new org.bukkit.Location(islandNetherLocation.getWorld(), islandNetherLocation.getBlockX(), 72, islandNetherLocation.getBlockZ()));
			islandStorage.put(islandOwnerUUID, island);
			
			Bukkit.getServer().getPluginManager().callEvent(new IslandLoadEvent(island));
		}
	}

	public void unloadIsland(Island island, UUID uuid) {
		ScoreboardManager scoreboardManager = skyblock.getScoreboardManager();
		FileManager fileManager = skyblock.getFileManager();
		
		Config config = fileManager.getConfig(new File(skyblock.getDataFolder(), "language.yml"));
		FileConfiguration configLoad = config.getFileConfiguration();
		
		island.save();
		
		int islandMembers = island.getRole(Role.Member).size() + island.getRole(Role.Operator).size() + 1, islandVisitors = getVisitorsAtIsland(island).size();
		boolean unloadIsland = true;
		
		for (Player all : Bukkit.getOnlinePlayers()) {
			if (uuid != null && all.getUniqueId().equals(uuid)) {
				continue;
			}
			
			if (island.isRole(Role.Member, all.getUniqueId()) || island.isRole(Role.Operator, all.getUniqueId()) || island.isRole(Role.Owner, all.getUniqueId())) {
				if (scoreboardManager != null) {
					try {
						if (islandMembers == 1 && islandVisitors == 0) {
							Scoreboard scoreboard = scoreboardManager.getScoreboard(all);
							scoreboard.cancel();
							scoreboard.setDisplayName(ChatColor.translateAlternateColorCodes('&', configLoad.getString("Scoreboard.Island.Solo.Displayname")));
							scoreboard.setDisplayList(configLoad.getStringList("Scoreboard.Island.Solo.Empty.Displaylines"));
							scoreboard.run();
						} else if (islandVisitors == 0) {
							Scoreboard scoreboard = scoreboardManager.getScoreboard(all);
							scoreboard.cancel();
							scoreboard.setDisplayName(ChatColor.translateAlternateColorCodes('&', configLoad.getString("Scoreboard.Island.Team.Displayname")));
							scoreboard.setDisplayList(configLoad.getStringList("Scoreboard.Island.Team.Empty.Displaylines"));
							
							HashMap<String, String> displayVariables = new HashMap<>();
							displayVariables.put("%owner", configLoad.getString("Scoreboard.Island.Team.Word.Owner"));
							displayVariables.put("%operator", configLoad.getString("Scoreboard.Island.Team.Word.Operator"));
							displayVariables.put("%member", configLoad.getString("Scoreboard.Island.Team.Word.Member"));
							
							scoreboard.setDisplayVariables(displayVariables);
							scoreboard.run();
						}
					} catch (IllegalPluginAccessException e) {}	
				}
				
				unloadIsland = false;
			}
		}
		
		if (!unloadIsland) {
			return;
		}
		
		unloadIsland = fileManager.getConfig(new File(skyblock.getDataFolder(), "config.yml")).getFileConfiguration().getBoolean("Island.Visitor.Unload");
		
		if (unloadIsland) {
			VisitManager visitManager = skyblock.getVisitManager();
			visitManager.removeVisitors(island, VisitManager.Removal.Unloaded);
			visitManager.unloadIsland(island.getOwnerUUID());
			
			BanManager banManager = skyblock.getBanManager();
			banManager.unloadIsland(island.getOwnerUUID());
		} else {
			int nonIslandMembers = islandVisitors - getCoopPlayersAtIsland(island).size();
			
			if (nonIslandMembers <= 0) {
				removeCoopPlayers(island, uuid);
			} else {
				return;
			}
		}
		
		fileManager.unloadConfig(new File(new File(skyblock.getDataFolder().toString() + "/coop-data"), island.getOwnerUUID() + ".yml"));
		fileManager.unloadConfig(new File(new File(skyblock.getDataFolder().toString() + "/setting-data"), island.getOwnerUUID() + ".yml"));
		fileManager.unloadConfig(new File(new File(skyblock.getDataFolder().toString() + "/island-data"), island.getOwnerUUID() + ".yml"));
		
		islandStorage.remove(island.getOwnerUUID());
		
		Bukkit.getServer().getPluginManager().callEvent(new IslandUnloadEvent(island));
	}
	
	public List<UUID> getVisitorsAtIsland(Island island) {
		Map<UUID, PlayerData> playerDataStorage = skyblock.getPlayerDataManager().getPlayerData();
		List<UUID> islandVisitors = new ArrayList<>();
		
		for (UUID playerDataStorageList : playerDataStorage.keySet()) {
			PlayerData playerData = playerDataStorage.get(playerDataStorageList);
			UUID islandOwnerUUID = playerData.getIsland();
			
			if (islandOwnerUUID != null && islandOwnerUUID.equals(island.getOwnerUUID())) {
				if (playerData.getOwner() == null || !playerData.getOwner().equals(island.getOwnerUUID())) {
					if (Bukkit.getServer().getPlayer(playerDataStorageList) != null) {
						islandVisitors.add(playerDataStorageList);
					}
				}
			}
		}
		
		return islandVisitors;
	}
	
	public void visitIsland(Player player, Island island) {
		ScoreboardManager scoreboardManager = skyblock.getScoreboardManager();
		FileManager fileManager = skyblock.getFileManager();
		
		Config languageConfig = fileManager.getConfig(new File(skyblock.getDataFolder(), "language.yml"));
		FileConfiguration configLoad = languageConfig.getFileConfiguration();
		
		if (island.isRole(Role.Member, player.getUniqueId()) || island.isRole(Role.Operator, player.getUniqueId()) || island.isRole(Role.Owner, player.getUniqueId())) {
			player.teleport(island.getLocation(Location.World.Normal, Location.Environment.Visitor));
		} else {
			if (scoreboardManager != null) {
				int islandVisitors = getVisitorsAtIsland(island).size(), islandMembers = island.getRole(Role.Member).size() + island.getRole(Role.Operator).size() + 1;
				
				if (islandVisitors == 0) {
					for (Player all : Bukkit.getOnlinePlayers()) {
						PlayerData targetPlayerData = skyblock.getPlayerDataManager().getPlayerData(all);
						
						if (targetPlayerData.getOwner() != null && targetPlayerData.getOwner().equals(island.getOwnerUUID())) {
							Scoreboard scoreboard = scoreboardManager.getScoreboard(all);
							scoreboard.cancel();
							
							if (islandMembers == 1) {
								scoreboard.setDisplayName(ChatColor.translateAlternateColorCodes('&', configLoad.getString("Scoreboard.Island.Solo.Displayname")));
								scoreboard.setDisplayList(configLoad.getStringList("Scoreboard.Island.Solo.Occupied.Displaylines"));
							} else {
								scoreboard.setDisplayName(ChatColor.translateAlternateColorCodes('&', configLoad.getString("Scoreboard.Island.Team.Displayname")));
								scoreboard.setDisplayList(configLoad.getStringList("Scoreboard.Island.Team.Occupied.Displaylines"));
								
								HashMap<String, String> displayVariables = new HashMap<>();
								displayVariables.put("%owner", configLoad.getString("Scoreboard.Island.Team.Word.Owner"));
								displayVariables.put("%operator", configLoad.getString("Scoreboard.Island.Team.Word.Operator"));
								displayVariables.put("%member", configLoad.getString("Scoreboard.Island.Team.Word.Member"));
								
								scoreboard.setDisplayVariables(displayVariables);
							}
							
							scoreboard.run();
						}
					}
				}	
			}
			
			Bukkit.getServer().getScheduler().runTask(skyblock, new Runnable() {
				@Override
				public void run() {
					player.teleport(island.getLocation(Location.World.Normal, Location.Environment.Visitor));
				}
			});
			
			List<String> islandWelcomeMessage = island.getMessage(Message.Welcome);
			
			if (skyblock.getFileManager().getConfig(new File(skyblock.getDataFolder(), "config.yml")).getFileConfiguration().getBoolean("Island.Visitor.Welcome.Enable") && islandWelcomeMessage.size() != 0) {
				for (String islandWelcomeMessageList : islandWelcomeMessage) {
					player.sendMessage(ChatColor.translateAlternateColorCodes('&', islandWelcomeMessageList));
				}
			}
		}
		
		player.closeInventory();
	}
	
	public void closeIsland(Island island) {
		MessageManager messageManager = skyblock.getMessageManager();
		
		Config config = skyblock.getFileManager().getConfig(new File(skyblock.getDataFolder(), "language.yml"));
		FileConfiguration configLoad = config.getFileConfiguration();
		
		island.setOpen(false);
		
		UUID islandOwnerUUID = island.getOwnerUUID();
		Player islandOwnerPlayer = Bukkit.getServer().getPlayer(islandOwnerUUID);
		String islandOwnerPlayerName;
		
		if (islandOwnerPlayer == null) {
			islandOwnerPlayerName = new OfflinePlayer(islandOwnerUUID).getName();
		} else {
			islandOwnerPlayerName = islandOwnerPlayer.getName();
		}
		
		for (UUID visitorList : getVisitorsAtIsland(island)) {
			if (!island.isCoopPlayer(visitorList)) {
	    		Player targetPlayer = Bukkit.getServer().getPlayer(visitorList);
				LocationUtil.teleportPlayerToSpawn(targetPlayer);
				messageManager.sendMessage(targetPlayer, configLoad.getString("Island.Visit.Closed.Island.Message").replace("%player", islandOwnerPlayerName));	
			}
		}
	}
	
	public Island getIsland(UUID islandOwnerUUID) {
		if (islandStorage.containsKey(islandOwnerUUID)) {
			return islandStorage.get(islandOwnerUUID);
		}
		
		return null;
	}
	
	public void removeIsland(UUID islandOwnerUUID) {
		islandStorage.remove(islandOwnerUUID);
	}

	public Map<UUID, Island> getIslands() {
		return islandStorage;
	}
	
	public boolean isIslandExist(UUID uuid) {
		return skyblock.getFileManager().isFileExist(new File(new File(skyblock.getDataFolder().toString() + "/island-data"), uuid.toString() + ".yml"));
	}
	
	public boolean hasIsland(UUID uuid) {
		UUID islandOwnerUUID = uuid;
		
		if (!isIslandExist(uuid)) {
			Config config = skyblock.getFileManager().getConfig(new File(new File(skyblock.getDataFolder().toString() + "/player-data"), uuid.toString() + ".yml"));
			FileConfiguration configLoad = config.getFileConfiguration();
			
			if (configLoad.getString("Island.Owner") != null) {
				islandOwnerUUID = UUID.fromString(configLoad.getString("Island.Owner"));
			}
		}
		
		return islandStorage.containsKey(islandOwnerUUID);
	}
	
	public boolean hasIsland(Player player) {
		PlayerData playerData = skyblock.getPlayerDataManager().getPlayerData(player);
		return (playerData == null) ? hasIsland(player.getUniqueId()) : islandStorage.containsKey(playerData.getOwner());
	}
	
	public boolean containsIsland(UUID uuid) {
		return islandStorage.containsKey(uuid);
	}
	
	public boolean hasPermission(Player player, String setting) {
		if (hasIsland(player)) {
			Island island = getIsland(skyblock.getPlayerDataManager().getPlayerData(player).getOwner());
			
			for (Location.World worldList : Location.World.values()) {
				if (LocationUtil.isLocationAtLocationRadius(player.getLocation(), island.getLocation(worldList, Location.Environment.Island), island.getRadius())) {
					if (island.isRole(Role.Member, player.getUniqueId())) {
						if (!island.getSetting(Setting.Role.Member, setting).getStatus()) {
							return false;
						}
					}
					
					return true;
				}
			}
		}
		
		for (UUID islandList : getIslands().keySet()) {
			Island island = getIslands().get(islandList);
			
			for (Location.World worldList : Location.World.values()) {
				if (LocationUtil.isLocationAtLocationRadius(player.getLocation(), island.getLocation(worldList, Location.Environment.Island), island.getRadius())) {
					if (player.hasPermission("skyblock.bypass." + setting.toLowerCase()) || player.hasPermission("skyblock.bypass.*") || player.hasPermission("skyblock.*")) {
						return true;
					} else if (island.isCoopPlayer(player.getUniqueId())) {
						if (!island.getSetting(Setting.Role.Coop, setting).getStatus()) {
							return false;
						}
					} else if (!island.getSetting(Setting.Role.Visitor, setting).getStatus()) {
						return false;
					}
					
					return true;
				}
			}
		}
		
		return true;
	}
	
	public void setSpawnProtection(org.bukkit.Location location) {
		location.getBlock().setType(Materials.LEGACY_PISTON_MOVING_PIECE.getPostMaterial());
		location.clone().add(0.0D, 1.0D, 0.0D).getBlock().setType(Materials.LEGACY_PISTON_MOVING_PIECE.getPostMaterial());
	}
	
	public void removeSpawnProtection(org.bukkit.Location location) {
		location.getBlock().setType(Material.AIR);
		location.clone().add(0.0D, 1.0D, 0.0D).getBlock().setType(Material.AIR);
	}
	
	public List<UUID> getMembersOnline(Island island) {
		List<UUID> membersOnline = new ArrayList<>();
		
		for (Player all : Bukkit.getOnlinePlayers()) {
			if (island.isRole(Role.Member, all.getUniqueId()) || island.isRole(Role.Operator, all.getUniqueId()) || island.isRole(Role.Owner, all.getUniqueId())) {
				membersOnline.add(all.getUniqueId());
			}
		}
		
		return membersOnline;
	}
	
	public List<UUID> getPlayersAtIsland(Island island) {
		List<UUID> playersAtIsland = new ArrayList<>();
		
		if (island != null) {
			for (Player all : Bukkit.getOnlinePlayers()) {
				for (Location.World worldList : Location.World.values()) {
					if (LocationUtil.isLocationAtLocationRadius(all.getLocation(), island.getLocation(worldList, Location.Environment.Island), island.getRadius())) {
						playersAtIsland.add(all.getUniqueId());
					}
				}
			}
		}
		
		return playersAtIsland;
	}
	
	public List<Player> getPlayersAtIsland(Island island, Location.World world) {
		List<Player> playersAtIsland = new ArrayList<>();
		
		for (Player all : Bukkit.getOnlinePlayers()) {
			if (LocationUtil.isLocationAtLocationRadius(all.getLocation(), island.getLocation(world, Location.Environment.Island), island.getRadius())) {
				playersAtIsland.add(all);
			}
		}
		
		return playersAtIsland;
	}
	
	public void loadPlayer(Player player) {
		Bukkit.getServer().getScheduler().runTaskAsynchronously(skyblock, new Runnable() {
			@Override
			public void run() {
				if (player.getWorld().getName().equals(skyblock.getWorldManager().getWorld(Location.World.Normal).getName())) {
					Island island = null;
					
					if (hasIsland(player)) {
						island = getIsland(skyblock.getPlayerDataManager().getPlayerData(player).getOwner());
						
						if (!LocationUtil.isLocationAtLocationRadius(player.getLocation(), island.getLocation(Location.World.Normal, Location.Environment.Island), island.getRadius())) {
							island = null;
						}
					}
					
					if (island == null) {
						for (UUID islandList : getIslands().keySet()) {
							Island targetIsland = getIslands().get(islandList);
							
							if (LocationUtil.isLocationAtLocationRadius(player.getLocation(), targetIsland.getLocation(Location.World.Normal, Location.Environment.Island), targetIsland.getRadius())) {
								island = targetIsland;
								
								break;
							}
						}
					}
					
					if (island != null) {
						Config config = skyblock.getFileManager().getConfig(new File(skyblock.getDataFolder(), "config.yml"));
						FileConfiguration configLoad = config.getFileConfiguration();
						
						if (!island.isWeatherSynchronised()) {
							player.setPlayerTime(island.getTime(), configLoad.getBoolean("Island.Weather.Time.Cycle"));
							player.setPlayerWeather(island.getWeather());	
						}
						
						if (configLoad.getBoolean("Island.WorldBorder.Enable")) {
							WorldBorder.send(player, island.getSize() + 2.5, island.getLocation(Location.World.Normal, Location.Environment.Island));
						}
						
						giveUpgrades(player, island);
					}
				}
			}
		});
	}
	
	public void giveUpgrades(Player player, Island island) {
		UpgradeManager upgradeManager = skyblock.getUpgradeManager();
		
		List<Upgrade> upgrades = upgradeManager.getUpgrades(Upgrade.Type.Speed);
    	
    	if (upgrades != null && upgrades.size() > 0 && upgrades.get(0).isEnabled() && island.isUpgrade(Upgrade.Type.Speed)) {
    		Bukkit.getServer().getScheduler().runTask(skyblock, new Runnable() {
				@Override
				public void run() {
					player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
				}
    		});
    	}
    	
    	upgrades = upgradeManager.getUpgrades(Upgrade.Type.Jump);
    	
    	if (upgrades != null && upgrades.size() > 0 && upgrades.get(0).isEnabled() && island.isUpgrade(Upgrade.Type.Jump)) {
    		Bukkit.getServer().getScheduler().runTask(skyblock, new Runnable() {
				@Override
				public void run() {
					player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 1));
				}
    		});
    	}
    	
    	upgrades = upgradeManager.getUpgrades(Upgrade.Type.Fly);
    	
    	if (upgrades != null && upgrades.size() > 0 && upgrades.get(0).isEnabled() && island.isUpgrade(Upgrade.Type.Fly)) {
    		Bukkit.getServer().getScheduler().runTask(skyblock, new Runnable() {
				@Override
				public void run() {
					player.setAllowFlight(true);
					player.setFlying(true);
				}
    		});
    	}
	}
	
	public void removeUpgrades(Player player) {
		player.removePotionEffect(PotionEffectType.SPEED);
		player.removePotionEffect(PotionEffectType.JUMP);
		
		if (player.getGameMode() != GameMode.CREATIVE) {
			player.setFlying(false);
			player.setAllowFlight(false);
		}
	}
	
	public List<UUID> getCoopPlayersAtIsland(Island island) {
		List<UUID> coopPlayersAtIsland = new ArrayList<>();
		
		if (island != null) {
			for (Player all : Bukkit.getOnlinePlayers()) {
				if (island.getCoopPlayers().contains(all.getUniqueId())) {
					for (Location.World worldList : Location.World.values()) {
						if (LocationUtil.isLocationAtLocationRadius(all.getLocation(), island.getLocation(worldList, Location.Environment.Island), island.getRadius())) {
							coopPlayersAtIsland.add(all.getUniqueId());
						}
					}
				}
			}
		}
		
		return coopPlayersAtIsland;
	}
	
	public boolean removeCoopPlayers(Island island, UUID uuid) {
		MessageManager messageManager = skyblock.getMessageManager();
		SoundManager soundManager = skyblock.getSoundManager();
		
		Config config = skyblock.getFileManager().getConfig(new File(skyblock.getDataFolder(), "language.yml"));
		FileConfiguration configLoad = config.getFileConfiguration();
		
		boolean coopPlayers = island.getSetting(Setting.Role.Operator, "CoopPlayers").getStatus();
		
		for (Player all : Bukkit.getOnlinePlayers()) {
			if (uuid != null && all.getUniqueId().equals(uuid)) {
				continue;
			}
			
			if (island.isRole(Role.Owner, all.getUniqueId())) {
				return false;
			} else if (coopPlayers && island.isRole(Role.Operator, all.getUniqueId())) {
				return false;
			}
		}
		
		for (UUID coopPlayerAtIslandList : getCoopPlayersAtIsland(island)) {
			Player targetPlayer = Bukkit.getServer().getPlayer(coopPlayerAtIslandList);
			
			if (targetPlayer != null) {
				LocationUtil.teleportPlayerToSpawn(targetPlayer);
				
				if (coopPlayers) {
					messageManager.sendMessage(targetPlayer, configLoad.getString("Island.Coop.Removed.Operator.Message"));
				} else {
					messageManager.sendMessage(targetPlayer, configLoad.getString("Island.Coop.Removed.Owner.Message"));
				}
				
				soundManager.playSound(targetPlayer, Sounds.IRONGOLEM_HIT.bukkitSound(), 1.0F, 1.0F);
			}
		}
		
		return true;
	}
}