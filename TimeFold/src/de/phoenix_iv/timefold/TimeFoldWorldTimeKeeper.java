package de.phoenix_iv.timefold;

import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * @author Tobias aka Phoenix
 */
public class TimeFoldWorldTimeKeeper {
	
	// The "official" numbers are on the right side. The used ones are based on visual studies (visible light/illumination).
	public final static long DAY     =   700;  //     0
	public final static long SUNSET  = 11500;  // 12000
	public final static long NIGHT   = 13700;  // 14000
	public final static long SUNRISE = 21900;  // 22000
	
	private final static long DURATION_DAY   = SUNSET - DAY;
	private final static long DURATION_NIGHT = SUNRISE - NIGHT;
	
	private TimeFold plugin;
	private World world;
	private long currentCycle;
	private double remainingRepetitions;
	private int runningTaskId = -1;
	
	protected TimeFoldWorldTimeKeeper(TimeFold plugin, World world) {
		this.plugin = plugin;
		this.world = world;
		this.start();
	}
	
	private void start() {
		Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			
			// This whole thing is inside a synchronized task because otherwise the scheduler is trolling for some
			// reason (actual delay differs by two ticks in average) which causes the time-change detection to fail
			
			@Override
			public void run() {
				// The following code will initialize the actual TimeKeeper.
				// It makes sure that there won't be an obvious time change; if it's night it will run "nights" and
				// dawn/dusk will be temporized.
				double days = TimeFoldFileHandler.getDays(world.getName());
				double nights = TimeFoldFileHandler.getNights(world.getName());
				long time = world.getTime();
				if (days == 0) {
					currentCycle = NIGHT;
					(new TimeKeeper()).run();
				} else if (nights == 0) {
					currentCycle = DAY;
					(new TimeKeeper()).run();
				} else if (time >= SUNRISE) {
					currentCycle = DAY;
					remainingRepetitions = days;
					runningTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new TimeKeeper(), (24000 - time) + DAY);
				} else if (time < DAY) {
					currentCycle = DAY;
					remainingRepetitions = days;
					runningTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new TimeKeeper(), DAY - time);
				} else if (time >= SUNSET && time < NIGHT) {
					currentCycle = NIGHT;
					remainingRepetitions = nights;
					runningTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new TimeKeeper(), NIGHT - time);
				} else if (time >= NIGHT) {
					currentCycle = NIGHT;
					remainingRepetitions = nights;
					(new TimeKeeper()).run();
				} else {
					currentCycle = DAY;
					remainingRepetitions = days;
					(new TimeKeeper()).run();
				}
			}
			
		}, 0L);
	}
	
	/**
	 * Gets the world the TimeKeeper is in charge of.
	 * @return The world
	 */
	public World getWorld() {
		return world;
	}
	
	/**
	 * Get the daytime of this world.
	 * @return true if it's day
	 */
	public boolean isDay() {
		long time = world.getTime();
		if (time >= SUNRISE || time < SUNSET) {
			return true;
		}
		return false;
	}
	
	/**
	 * Get the daytime of this world.
	 * @return One of the static longs whcih represent daytimes
	 */
	public long getDaytime() {
		long time = world.getTime();
		if (time >= DAY && time < SUNSET) {
			return DAY;
		} else if (time >= SUNSET && time < NIGHT) {
			return SUNSET;
		} else if (time >= NIGHT && time < SUNRISE) {
			return NIGHT;
		} else {
			return SUNRISE;
		}
	}
	
	/**
	 * Remaining repetitions in the current cycle.
	 * @return
	 */
	public double getRemainingRepetitions() {
		return remainingRepetitions;
	}
	
	/**
	 * This will stop changing the world's time (the responsible task).
	 */
	public void stop() {
		if (runningTaskId != -1) {
			Bukkit.getScheduler().cancelTask(runningTaskId);
			runningTaskId = -1;
		}
	}
	

	private class TimeKeeper implements Runnable {

		@Override
		public void run() {
			// This first part detects if the time meanwhile was changed by another authority
			// and restarts the TimeKeeper if so
			if (runningTaskId != -1) {
				// If this isn't a scheduled run we can't be sure about the expected time
				long time = world.getTime();
				if ((currentCycle == DAY && time != SUNSET && time != DAY) || (currentCycle == NIGHT && time != SUNRISE && time != NIGHT)) {
					// The condition isn't 100% accurate, but it's enough 
					TimeFold.log("Detected a timechange by a third party in world \"" + world.getName() + "\". Restarting the responsible TimeKeeper.");
					// The current thread is 'stopped' because we don't reschedule it.
					runningTaskId = -1;
					start();
					return;
				}
			}
			
			if (currentCycle == DAY) {
				if (remainingRepetitions >= 1) {
					world.setTime(DAY);
					remainingRepetitions -= 1;
					this.reshedule(DURATION_DAY);
				} else if (remainingRepetitions > 0) {
					long setBackBy = (long) (DURATION_DAY * remainingRepetitions);
					world.setTime(SUNSET - setBackBy);
					remainingRepetitions = 0;
					this.reshedule(setBackBy);
				} else {
					remainingRepetitions = TimeFoldFileHandler.getNights(world.getName());
					if (remainingRepetitions == 0) {
						world.setTime(DAY);
						this.reshedule(DURATION_DAY);
					} else {
						currentCycle = NIGHT;
						this.reshedule(NIGHT - SUNSET);
					}
				}
			} else {
				if (remainingRepetitions >= 1) {
					world.setTime(NIGHT);
					remainingRepetitions -= 1;
					this.reshedule(DURATION_NIGHT);
				} else if (remainingRepetitions > 0) {
					long setBackBy = (long) (DURATION_NIGHT * remainingRepetitions);
					world.setTime(SUNRISE - setBackBy);
					remainingRepetitions = 0;
					this.reshedule(setBackBy);
				} else {
					remainingRepetitions = TimeFoldFileHandler.getDays(world.getName());
					if (remainingRepetitions == 0) {
						world.setTime(NIGHT);
						this.reshedule(DURATION_NIGHT);
					} else {
						currentCycle = DAY;
						this.reshedule((24000 - SUNRISE) + DAY);
					}
				}
			}
		}
		
		private void reshedule(long delay) {
			runningTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this, delay);
		}
		
	}
	
}
