package me.goodandevil.skyblock.command.commands.admin;

import me.goodandevil.skyblock.command.SubCommand;
import me.goodandevil.skyblock.menus.admin.Settings;
import me.goodandevil.skyblock.sound.SoundManager;
import me.goodandevil.skyblock.utils.version.Sounds;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class SettingsCommand extends SubCommand {

	@Override
	public void onCommandByPlayer(Player player, String[] args) {
		SoundManager soundManager = skyblock.getSoundManager();

		Settings.getInstance().open(player, Settings.Type.Categories, null);
		soundManager.playSound(player, Sounds.CHEST_OPEN.bukkitSound(), 1.0F, 1.0F);
	}

	@Override
	public void onCommandByConsole(ConsoleCommandSender sender, String[] args) {
		sender.sendMessage("SkyBlock | Error: You must be a player to perform that command.");
	}

	@Override
	public String getName() {
		return "settings";
	}

	@Override
	public String getInfoMessagePath() {
		return "Command.Island.Admin.Settings.Info.Message";
	}

	@Override
	public String[] getAliases() {
		return new String[] { "permissions", "perms", "p" };
	}

	@Override
	public String[] getArguments() {
		return new String[0];
	}
}
