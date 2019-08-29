package com.songoda.skyblock.usercache;

import com.songoda.skyblock.SkyBlock;
import com.songoda.skyblock.config.FileManager;
import com.songoda.skyblock.config.FileManager.Config;
import com.songoda.skyblock.utils.player.NameFetcher;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class UserCacheManager {

    private final SkyBlock skyblock;
    private final Config config;

    public UserCacheManager(SkyBlock skyblock) {
        this.skyblock = skyblock;
        this.config = skyblock.getFileManager().getConfig(new File(skyblock.getDataFolder(), "usercache.yml"));

        FileManager fileManager = skyblock.getFileManager();
        File configFile = new File(skyblock.getDataFolder().toString() + "/island-data");

        Bukkit.getServer().getScheduler().runTaskAsynchronously(skyblock, () -> {
            if (configFile.exists()) {
                int usersIgnored = 0;

                Bukkit.getServer().getLogger().log(Level.INFO,
                        "SkyBlock | Info: Fetching user information from island data. This may take a while...");

                for (File fileList : configFile.listFiles()) {
                    if (fileList != null && fileList.getName().contains(".yml")
                            && fileList.getName().length() > 35) {
                        UUID islandOwnerUUID = null;

                        try {
                            Config config = new Config(fileManager, fileList);
                            FileConfiguration configLoad = config.getFileConfiguration();

                            islandOwnerUUID = UUID.fromString(fileList.getName().replace(".yml", ""));

                            if (islandOwnerUUID == null) {
                                islandOwnerUUID = UUID.fromString(fileList.getName().replaceFirst("[.][^.]+$", ""));

                                if (islandOwnerUUID == null) {
                                    continue;
                                }
                            }

                            Set<UUID> islandMembers = new HashSet<>();
                            islandMembers.add(islandOwnerUUID);

                            if (configLoad.getString("Members") != null) {
                                for (String memberList : configLoad.getStringList("Members")) {
                                    islandMembers.add(UUID.fromString(memberList));
                                }
                            }

                            if (configLoad.getString("Operators") != null) {
                                for (String operatorList : configLoad.getStringList("Operators")) {
                                    islandMembers.add(UUID.fromString(operatorList));
                                }
                            }

                            for (UUID islandMemberList : islandMembers) {
                                if (!hasUser(islandMemberList)) {
                                    NameFetcher.Names[] names = NameFetcher.getNames(islandMemberList);

                                    if (names.length >= 1) {
                                        addUser(islandMemberList, names[0].getName());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            usersIgnored++;
                        }
                    }
                }

                save();

                if (usersIgnored != 0) {
                    Bukkit.getServer().getLogger().log(Level.INFO,
                            "SkyBlock | Info: Finished fetching user information from island data. There were "
                                    + usersIgnored + " users that were skipped.");
                } else {
                    Bukkit.getServer().getLogger().log(Level.INFO,
                            "SkyBlock | Info: Finished fetching user information from island data.");
                }
            }
        });
    }

    public void onDisable() {
        save();
    }

    public void addUser(UUID uuid, String name) {
        config.getFileConfiguration().set(uuid.toString(), name);
    }

    public String getUser(UUID uuid) {
        FileConfiguration configLoad = config.getFileConfiguration();

        if (configLoad.getString(uuid.toString()) != null) {
            return configLoad.getString(uuid.toString());
        }

        return null;
    }

    public UUID getUser(String name) {
        FileConfiguration configLoad = config.getFileConfiguration();

        for (String userList : configLoad.getConfigurationSection("").getKeys(false)) {
            if (configLoad.getString(userList).equalsIgnoreCase(name)) {
                return UUID.fromString(userList);
            }
        }

        return null;
    }

    public boolean hasUser(UUID uuid) {
        return config.getFileConfiguration().getString(uuid.toString()) != null;
    }

    public boolean hasUser(String name) {
        FileConfiguration configLoad = config.getFileConfiguration();

        for (String userList : configLoad.getConfigurationSection("").getKeys(false)) {
            if (configLoad.getString(userList).equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    public void saveAsync() {
        Bukkit.getServer().getScheduler().runTaskAsynchronously(skyblock, () -> save());
    }

    public void save() {
        try {
            config.getFileConfiguration().save(config.getFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}