package com.elmakers.mine.bukkit.utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;


public class Messages {
	public static Map<String, String> messageMap = new HashMap<String, String>();
	public static ConfigurationSection configuration = null;
	
	public static void load(ConfigurationSection messages) {
		configuration = messages;
		Collection<String> keys = messages.getKeys(true);
		for (String key : keys) {
			messageMap.put(key, messages.getString(key));
		}
	}
	
	public static List<String> getAll(String path) {
		if (configuration == null) return new ArrayList<String>();
		return configuration.getStringList(path);
	}
	
	public static void reset() {
		messageMap.clear();
	}
	
	public static String get(String key, String defaultValue) {
		return messageMap.containsKey(key) ? ChatColor.translateAlternateColorCodes('&', messageMap.get(key)) : defaultValue;
	}
	
	public static String get(String key) {
		return get(key, key);
	}
	
	public static String getParameterized(String key, String paramName, String paramValue) {
		return get(key, key).replace(paramName, paramValue);
	}
	
	public static String getParameterized(String key, String paramName1, String paramValue1, String paramName2, String paramValue2) {
		return get(key, key).replace(paramName1, paramValue1).replace(paramName2, paramValue2);
	}
}