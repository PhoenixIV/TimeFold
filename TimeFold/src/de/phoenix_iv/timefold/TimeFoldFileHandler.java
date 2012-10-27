package de.phoenix_iv.timefold;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;


/**
 * TimeFold file handler
 * 
 * @author Tobias aka Phoenix
 * @author Silverstar
 */
public class TimeFoldFileHandler {
	public static Map<String,Double[]> worlds = new HashMap<String,Double[]>();
	public static Double[] defaults = new Double[]{1.0, 1.0};
	private final static File configFile = new File("plugins" + File.separator + "TimeFold", "TimeFold.settings");

	public static void loadConfig(){
		if(!configFile.exists()){
			try {
				configFile.getParentFile().mkdirs();
				FileOutputStream stream = new FileOutputStream(configFile);
				DataOutputStream out = new DataOutputStream(stream);
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
				bw.write("[default]:1:1");
				bw.close();
				TimeFold.log("TimeFold settings file created");
			}catch(Exception e){
				TimeFold.log(Level.SEVERE, "Can't create the TimeFold settings file");
			}
		}else{
			try {
				FileInputStream stream = new FileInputStream(configFile);
				DataInputStream in = new DataInputStream(stream);
				BufferedReader br = new BufferedReader(new InputStreamReader(in));
				String strLine;
				while((strLine = br.readLine()) != null){
					String[] line = strLine.split(":", 4);
					if(line.length == 3){
						double days = 1;
						double nights = 1;
						
						try{
							days = Double.parseDouble(line[1]);
						}catch(NumberFormatException e){
							TimeFold.log(Level.WARNING, "Couldn't parse days for world \"" + line[0] + "\" - Setting days to 1");
						}
						try{
							nights = Double.parseDouble(line[2]);
						}catch(NumberFormatException e){
							TimeFold.log(Level.WARNING, "Couldn't parse nights for world \"" + line[0] + "\" - Setting nights to 1");
						}
						if(days < 0){
							TimeFold.log(Level.WARNING, "Days for world \"" + line[0] + "\" are smaller than 0 - Setting days to 1");
							days = 1;
						}
						if(nights < 0){
							TimeFold.log(Level.WARNING, "Nights for world \"" + line[0] + "\" are smaller than 0 - Setting nights to 1");
							nights = 1;
						}
						if(days == 0 && nights == 0){
							TimeFold.log(Level.WARNING, "Misconfiguration for world \"" + line[0] + "\" found - Setting days:nights to 1:1");
						}
						
						if(line[0].equals("[default]")){
							defaults[0] = days;
							defaults[1] = nights;
						}else{
							worlds.put(line[0], new Double[]{days, nights});
						}
					}else{
						TimeFold.log(Level.WARNING, "Misconfiguration for world \"" + line[0] + "\" found - Setting days:nights to 1:1");
					}
				}
				br.close();
			}catch (FileNotFoundException e){
				TimeFold.log(Level.SEVERE, "Can't find the TimeFold settings file");
			}catch (Exception e){
				TimeFold.log(Level.SEVERE, "Error while reading the TimeFold settings file");
			}
		}
	}
	
	public static double getDays(String worldName) {
		if(worlds.containsKey(worldName)) return worlds.get(worldName)[0];
		return defaults[0];
	}
	
	public static double getNights(String worldName) {
		if(worlds.containsKey(worldName)) return worlds.get(worldName)[1];
		return defaults[1];
	}
	
	
	
}