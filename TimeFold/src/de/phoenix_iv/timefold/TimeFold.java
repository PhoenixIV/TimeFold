package de.phoenix_iv.timefold;

import java.util.ArrayList;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TimeFold for Bukkit
 * 
 * Originally published by Silverstar on Sep 10, 2011
 * Taken up and rewritten by Phoenix in September 2012, who independently had the same plugin idea 
 *
 * @author Tobias aka Phoenix
 * @author Silverstar (Former author - Some code is still used)
 */
public class TimeFold extends JavaPlugin implements Listener {
	
	private static final Logger log = Logger.getLogger("Minecraft.TimeFold");
	private ArrayList<TimeFoldWorldTimeKeeper> timeKeepers = new ArrayList<TimeFoldWorldTimeKeeper>();

	public void onEnable() {
		TimeFoldFileHandler.loadConfig();
		Bukkit.getServer().getPluginManager().registerEvents(this, this);
		for (World world : Bukkit.getWorlds()) {
			addTimeKeeper(world);
		}
	}
	
	public void onDisable(){
		for (TimeFoldWorldTimeKeeper keeper : timeKeepers) {
			keeper.stop();
		}
		timeKeepers.clear();
	}
	
	@EventHandler
	public void onWorldLoad(WorldLoadEvent event) {
		addTimeKeeper(event.getWorld());
	}
	
	@EventHandler
	public void onWorldUnload(WorldUnloadEvent event) {
		TimeFoldWorldTimeKeeper timeKeeper = this.getTimeKeeper(event.getWorld());
		if (timeKeeper != null) {
			timeKeeper.stop();
			timeKeepers.remove(timeKeeper);
		}
	}

	public boolean onCommand(CommandSender sender, Command cmd, String cmdLabel, String args[]) {
		Player player = null;
		if (sender instanceof Player) {
			player = (Player) sender;
		}
		
		if (cmd.getName().equals("TimeFold")) {
			if (args.length == 0) {
				if (player != null) {
					player.sendMessage(getCycle(player.getWorld()));
				} else {
					sender.sendMessage("No world specified!");
					sender.sendMessage("Use \"timefold <worldname>\"");
				}
				return true;
			} else if (args.length == 1) {
				if (args[0].equalsIgnoreCase("reload")) {
					if (sender.hasPermission("timefold.reload")) {
						this.onDisable();
						this.onEnable();
						sender.sendMessage(ChatColor.DARK_GREEN + "TimeFold reloaded");
					} else {
						sender.sendMessage(ChatColor.RED + "You don't have permission to do this!");
					}
				} else if (args[0].equalsIgnoreCase("version")) {
					sender.sendMessage(this.getDescription().getFullName());
				} else if (args[0].equalsIgnoreCase("help")) {
					Bukkit.dispatchCommand(sender, "help TimeFold");
				} else {
					World world = Bukkit.getWorld(args[0]);
					if (world != null) {
						sender.sendMessage(getCycle(world));
					} else {
						sender.sendMessage("World " + args[0] + " not found!");
					}
				}
				return true;
			}
		}
		return false;
	}
	
	@EventHandler
	public void onPlayerBedEnter(PlayerBedEnterEvent event) {
		if (!event.isCancelled()) {
			Player player = event.getPlayer();
			double days = TimeFoldFileHandler.getDays(player.getWorld().getName());
			if (days == 0) {
				event.setCancelled(true);
				player.sendMessage("There is nothing but night in this world. You would never wake up again!");
			}
		}
	}
	
	public static void log(String s){
		log.info("[TimeFold] " + s);
	}
	
	public static void log(java.util.logging.Level level, String s){
		log.log(level, "[TimeFold] " + s);
	}
	
	public static String doubleToString(double d) {
		String s = Double.toString(d);
		if (s.endsWith(".0")) {
			s = s.substring(0, s.length()-2);
		}
		return s;
	}
	
	/**
	 * Get the TimeKeeper of a world.
	 * @param world The world.
	 * @return TimeKeeper if existed, otherwise null.
	 */
	public TimeFoldWorldTimeKeeper getTimeKeeper(World world) {
		for (TimeFoldWorldTimeKeeper keeper : timeKeepers) {
			if (keeper.getWorld() == world) return keeper;
		}
		return null;
	}

	/**
	 * Gives information about the day-night-cycle in a world.
	 * @param world World to look up.
	 * @return Prepared message.
	 */
	public String getCycle(World world) {
		Environment environment = world.getEnvironment();
		if (environment == Environment.NETHER) {
			return "There is no time in the Nether!";
		} else if (environment == Environment.THE_END) {
			return "There is no time in the End!";
		} else {
			double days   = TimeFoldFileHandler.getDays(world.getName());
			double nights = TimeFoldFileHandler.getNights(world.getName());
			if (nights == 0) {
				return "It's day all the time.";
			} else if(days == 0) {
				return "It's always night.";
			} else if (days == 1 && nights == 1) {
				return "Normal day/night cycle. Look up in the sky!";
			} else {
				TimeFoldWorldTimeKeeper keeper = getTimeKeeper(world);
				long daytime = keeper.getDaytime();
				if (daytime == TimeFoldWorldTimeKeeper.DAY) {
					int current = (int) Math.ceil(days - keeper.getRemainingRepetitions());
					StringBuilder message = new StringBuilder().append(ChatColor.WHITE).append("It's day ").append(ChatColor.YELLOW).append(current).append(ChatColor.WHITE).append(" of ").append(ChatColor.YELLOW).append(doubleToString(days));
					return message.toString();
				} else if (daytime == TimeFoldWorldTimeKeeper.NIGHT) {
					int current = (int) Math.ceil(nights - keeper.getRemainingRepetitions());
					StringBuilder message = new StringBuilder().append(ChatColor.WHITE).append("It's night ").append(ChatColor.YELLOW).append(current).append(ChatColor.WHITE).append(" of ").append(ChatColor.YELLOW).append(doubleToString(nights));
					return message.toString();
				} else if (daytime == TimeFoldWorldTimeKeeper.SUNRISE) {
					return ChatColor.WHITE + "It's sunrise";
				} else {
					return ChatColor.WHITE + "It's sunset";
				}
			}
		}
	}
	
	/**
	 * Creates a new TimeKeeper and adds it to the list. <br>
	 * If the world's time is natural / isn't changed (1:1) or there is no time in the environment (Nether, End)
	 * it won't be created.
	 * @param world World to create the TimeKeeper for.
	 * @return True if a new TimeKeeper was added or already existed. Otherwise false.
	 */
	public boolean addTimeKeeper(World world) {
		for (TimeFoldWorldTimeKeeper keeper : timeKeepers) {
			if (keeper.getWorld() == world) {
				return true;
			}
		}
		Environment environment = world.getEnvironment();
		if (environment != Environment.NETHER && environment != Environment.THE_END) {
			double days   = TimeFoldFileHandler.getDays(world.getName());
			double nights = TimeFoldFileHandler.getNights(world.getName());
			if (!(days == 1 && nights == 1)) {
				TimeFoldWorldTimeKeeper keeper = new TimeFoldWorldTimeKeeper(this, world);
				timeKeepers.add(keeper);
				return true;
			}
		}
		return false;
	}
}