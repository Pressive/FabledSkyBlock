package me.goodandevil.skyblock.command.commands.island;

import me.goodandevil.skyblock.command.SubCommand;
import me.goodandevil.skyblock.config.FileManager.Config;
import me.goodandevil.skyblock.menus.ControlPanel;
import me.goodandevil.skyblock.sound.SoundManager;
import me.goodandevil.skyblock.utils.version.Sounds;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

public class ControlPanelCommand extends SubCommand {

	@Override
	public void onCommandByPlayer(Player player, String[] args) {
		SoundManager soundManager = skyblock.getSoundManager();

		Config config = skyblock.getFileManager().getConfig(new File(skyblock.getDataFolder(), "language.yml"));
		FileConfiguration configLoad = config.getFileConfiguration();

		if (skyblock.getIslandManager().getIsland(player) == null) {
			skyblock.getMessageManager().sendMessage(player,
					configLoad.getString("Command.Island.ControlPanel.Owner.Message"));
			soundManager.playSound(player, Sounds.ANVIL_LAND.bukkitSound(), 1.0F, 1.0F);
		} else {
			ControlPanel.getInstance().open(player);
			soundManager.playSound(player, Sounds.CHEST_OPEN.bukkitSound(), 1.0F, 1.0F);
		}
	}

	@Override
	public void onCommandByConsole(ConsoleCommandSender sender, String[] args) {
		sender.sendMessage("SkyBlock | Error: You must be a player to perform that command.");
	}

	@Override
	public String getName() {
		return "controlpanel";
	}

	@Override
	public String getInfoMessagePath() {
		return "Command.Island.ControlPanel.Info.Message";
	}

	@Override
	public String[] getAliases() {
		return new String[] { "cp" };
	}

	@Override
	public String[] getArguments() {
		return new String[0];
	}
}
