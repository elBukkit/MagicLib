package com.elmakers.mine.bukkit.spell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.elmakers.mine.bukkit.api.effect.ParticleType;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.spell.CostReducer;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellEventType;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.api.spell.SpellTemplate;
import com.elmakers.mine.bukkit.api.spell.TargetType;
import com.elmakers.mine.bukkit.block.MaterialAndData;
import com.elmakers.mine.bukkit.effect.EffectPlayer;
import com.elmakers.mine.bukkit.effect.builtin.EffectSingle;
import com.elmakers.mine.bukkit.effect.builtin.EffectTrail;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.Messages;

public abstract class BaseSpell implements Comparable<SpellTemplate>, Cloneable, CostReducer, Spell {
	protected static final double VIEW_HEIGHT = 1.65;
	protected static final double LOOK_THRESHOLD_RADIANS = 0.8;
	private static final String EFFECT_BUILTIN_CLASSPATH = "com.elmakers.mine.bukkit.effect.builtin";
	
	// TODO: Configurable default? this does look cool, though.
	protected final static Material DEFAULT_EFFECT_MATERIAL = Material.STATIONARY_WATER;
	
	public final static String[] EXAMPLE_VECTOR_COMPONENTS = {"-1", "-0.5", "0", "0.5", "1", "~-1", "~-0.5", "~0", "~0.5", "*1", "*-1", "*-0.5", "*0.5", "*1"};
	public final static String[] EXAMPLE_SIZES = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "12", "16", "32", "64"};
	public final static String[] EXAMPLE_BOOLEANS = {"true", "false"};
	public final static String[] EXAMPLE_DURATIONS = {"500", "1000", "2000", "5000", "10000", "60000", "120000"};
	public final static String[] EXAMPLE_PERCENTAGES = {"0", "0.1", "0.25", "0.5", "0.75", "1"};
	
	public final static String[] OTHER_PARAMETERS = {
		"transparent", "target", "target_type", "range", "duration", "player"
	};
	
	public final static String[] WORLD_PARAMETERS = {
		"pworld", "tworld", "otworld", "t2world"
	};
	
	protected final static Set<String> worldParameterMap = new HashSet<String>(Arrays.asList(WORLD_PARAMETERS));
	
	public final static String[] VECTOR_PARAMETERS = {
		"px", "py", "pz", "pdx", "pdy", "pdz", "tx", "ty", "tz", "otx", "oty", "otz", "t2x", "t2y", "t2z"
	};

	protected final static Set<String> vectorParameterMap = new HashSet<String>(Arrays.asList(VECTOR_PARAMETERS));
	
	public final static String[] BOOLEAN_PARAMETERS = {
		"allow_max_range", "prevent_passthrough", "bypass_build", "bypass_pvp", "target_npc"
	};

	protected final static Set<String> booleanParameterMap = new HashSet<String>(Arrays.asList(BOOLEAN_PARAMETERS));
	
	public final static String[] PERCENTAGE_PARAMETERS = {
		"fizzle_chance", "backfire_chance", "cooldown_reduction"
	};
	
	protected final static Set<String> percentageParameterMap = new HashSet<String>(Arrays.asList(PERCENTAGE_PARAMETERS));
	
	public final static String[] COMMON_PARAMETERS = (String[])
		ArrayUtils.addAll(
			ArrayUtils.addAll(
					ArrayUtils.addAll(
							ArrayUtils.addAll(VECTOR_PARAMETERS, BOOLEAN_PARAMETERS), 
							OTHER_PARAMETERS
					),
					WORLD_PARAMETERS
			), 
			PERCENTAGE_PARAMETERS
		);
	

	/*
	 * protected members that are helpful to use
	 */
	protected MageController				controller;
	protected Mage 							mage;
	protected Location    					location;

	/*
	 * Variant properties
	 */
	private String key;
	private String name;
	private String description;
	private String usage;
	private String category;
	private MaterialAndData icon = new MaterialAndData(Material.AIR);
	private List<CastingCost> costs = null;
	private List<CastingCost> activeCosts = null;

	protected ConfigurationSection parameters = null;

	/*
	 * private data
	 */

	private float                               cooldownReduction       = 0;
	private float                               costReduction           = 0;
	
	private int                                 cooldown                = 0;
	private int                                 duration                = 0;
	private long                                lastCast                = 0;
	private long								castCount				= 0;

	private boolean								isActive				= false;
	
	private Map<SpellResult, List<EffectPlayer>> effects				= new HashMap<SpellResult, List<EffectPlayer>>();
	
	private float								fizzleChance			= 0.0f;
	private float								backfireChance			= 0.0f;
	
	private long 								lastMessageSent 			= 0;
	private Set<Material>						preventPassThroughMaterials = null;
	
	public Player getPlayer()
	{
		return mage.getPlayer();
	}

	public CommandSender getCommandSender()
	{
		return mage.getCommandSender();
	}

	public boolean allowPassThrough(Material mat)
	{
		if (mage != null && mage.isSuperPowered()) {
			return true;
		}
		return preventPassThroughMaterials == null || !preventPassThroughMaterials.contains(mat);
	}
	
	/*
	 * Ground / location search and test function functions
	 * TODO: Config-drive this.
	 */
	public boolean isOkToStandIn(Material mat)
	{
		return 
		(
			mat == Material.AIR 
			||    mat == Material.WATER 
			||    mat == Material.STATIONARY_WATER 
			||    mat == Material.SNOW
			||    mat == Material.TORCH
			||    mat == Material.SIGN_POST
			||    mat == Material.REDSTONE_TORCH_ON
			||    mat == Material.REDSTONE_TORCH_OFF
			||    mat == Material.YELLOW_FLOWER
			||    mat == Material.RED_ROSE
			||    mat == Material.RED_MUSHROOM
			||    mat == Material.BROWN_MUSHROOM
			||    mat == Material.LONG_GRASS
		);
	}

	public boolean isWater(Material mat)
	{
		return (mat == Material.WATER || mat == Material.STATIONARY_WATER);
	}

	public boolean isOkToStandOn(Material mat)
	{
		return (mat != Material.AIR && mat != Material.LAVA && mat != Material.STATIONARY_LAVA);
	}
	
	public boolean isSafeLocation(Block block)
	{
		
		if (!block.getChunk().isLoaded()) {
			block.getChunk().load(true);
			return false;
		}

		if (block.getY() > 255) {
			return false;
		}
		
		Block blockOneUp = block.getRelative(BlockFace.UP);
		Block blockOneDown = block.getRelative(BlockFace.DOWN);
		Player player = mage.getPlayer();
		return (
				(isOkToStandOn(blockOneDown.getType()) || (player != null && player.isFlying()))
				&&	isOkToStandIn(blockOneUp.getType())
				&& 	isOkToStandIn(block.getType())
		);
	}
	
	public boolean isSafeLocation(Location loc)
	{
		return isSafeLocation(loc.getBlock());
	}
	
	public Location tryFindPlaceToStand(Location targetLoc)
	{
		return tryFindPlaceToStand(targetLoc, 4, 253);
	}
	
	public Location tryFindPlaceToStand(Location targetLoc, int minY, int maxY)
	{
		Location location = findPlaceToStand(targetLoc, minY, maxY);
		return location == null ? targetLoc : location;
	}
	
	public Location findPlaceToStand(Location targetLoc, int minY, int maxY)
	{
		if (!targetLoc.getBlock().getChunk().isLoaded()) return null;
		
		int targetY = targetLoc.getBlockY();
		if (targetY >= minY && targetY <= maxY && isSafeLocation(targetLoc)) return targetLoc;
		
		Location location = null;
		if (targetY < minY) {
			location = targetLoc.clone();
			location.setY(minY);
			location = findPlaceToStand(location, true, minY, maxY);
		} else if (targetY > maxY) {
			location = targetLoc.clone();
			location.setY(maxY);
			location = findPlaceToStand(location, false, minY, maxY);
		} else {
			// First look down just a little bit
			int y = targetLoc.getBlockY();
			int testMinY = Math.max(minY,  y - 4);
			location = findPlaceToStand(targetLoc, false, testMinY, maxY);
			
			// Then look up
			if (location == null) {
				location = findPlaceToStand(targetLoc, true, minY, maxY);
			}
			
			// Then look allll the way down.
			if (location == null) {
				location = findPlaceToStand(targetLoc, false, minY, maxY);
			}
		}
		return location;
	}
	
	public Location findPlaceToStand(Location target, boolean goUp)
	{
		return findPlaceToStand(target, goUp, 4, 253);
	}
	
	public Location findPlaceToStand(Location target, boolean goUp, int minY, int maxY)
	{
		int direction = goUp ? 1 : -1;
		
		// search for a spot to stand
		Location targetLocation = target.clone();
		while (minY <= targetLocation.getY() && targetLocation.getY() <= maxY)
		{
			Block block = targetLocation.getBlock();
			if 
			(
				isSafeLocation(block)
			&&   !(goUp && isUnderwater() && isWater(block.getType())) // rise to surface of water
			)
			{
				// spot found - return location
				return targetLocation;
			}
			
			if (!allowPassThrough(block.getType())) {
				return null;
			}
			
			targetLocation.setY(targetLocation.getY() + direction);
		}

		// no spot found
		return null;
	}
	
	/**
	 * Get the block the player is standing on.
	 * 
	 * @return The Block the player is standing on
	 */
	public Block getPlayerBlock()
	{
		Location location = getLocation();
		if (location == null) return null;
		return location.getBlock().getRelative(BlockFace.DOWN);
	}

	/**
	 * Get the direction the player is facing as a BlockFace.
	 * 
	 * @return a BlockFace representing the direction the player is facing
	 */
	public BlockFace getPlayerFacing()
	{
		float playerRot = getLocation().getYaw();
		while (playerRot < 0)
			playerRot += 360;
		while (playerRot > 360)
			playerRot -= 360;

		BlockFace direction = BlockFace.NORTH;
		if (playerRot <= 45 || playerRot > 315)
		{
			direction = BlockFace.SOUTH;
		}
		else if (playerRot > 45 && playerRot <= 135)
		{
			direction = BlockFace.WEST;
		}
		else if (playerRot > 135 && playerRot <= 225)
		{
			direction = BlockFace.NORTH;
		}
		else if (playerRot > 225 && playerRot <= 315)
		{
			direction = BlockFace.EAST;
		}

		return direction;
	}	

	/*
	 * Functions to send text to player- use these to respect "quiet" and "silent" modes.
	 */

	/**
	 * Send a message to a player when a spell is cast.
	 * 
	 * @param message The message to send
	 */
	public void castMessage(String message)
	{
		if (canSendMessage() && message != null && message.length() > 0)
		{
			mage.castMessage(message);
			lastMessageSent = System.currentTimeMillis();
		}
	}

	/**
	 * Send a message to a player. 
	 * 
	 * Use this to send messages to the player that are important.
	 * 
	 * @param message The message to send
	 */
	public void sendMessage(String message)
	{
		if (canSendMessage() && message != null && message.length() > 0)
		{
			mage.sendMessage(message);
			lastMessageSent = System.currentTimeMillis();
		}
	}

	public Location getLocation()
	{
		if (location != null) return location.clone();
		if (mage != null) {
			return mage.getLocation();
		}
		return null;
	}

	public Location getEyeLocation()
	{
		Location location = getLocation();
		if (location == null) return null;
		location.setY(location.getY() + 1.5);
		return location;
	}
	
	public Vector getDirection()
	{
		if (location == null) {
			return mage.getDirection();
		}
		return location.getDirection();
	}
	
	public boolean isLookingUp()
	{
		Vector direction = getDirection();
		if (direction == null) return false;
		return direction.getY() > LOOK_THRESHOLD_RADIANS;
	}

	public boolean isLookingDown()
	{
		Vector direction = getDirection();
		if (direction == null) return false;
		return direction.getY() < -LOOK_THRESHOLD_RADIANS;
	}

	public World getWorld()
	{
		Location location = getLocation();
		if (location != null) return location.getWorld();
		return null;
	}

	/**
	 * Check to see if the player is underwater
	 * 
	 * @return true if the player is underwater
	 */
	public boolean isUnderwater()
	{
		Block playerBlock = getPlayerBlock();
		if (playerBlock == null) return false;
		playerBlock = playerBlock.getRelative(BlockFace.UP);
		return (playerBlock.getType() == Material.WATER || playerBlock.getType() == Material.STATIONARY_WATER);
	}
	
	protected String getBlockSkin(Material blockType) {
		String skinName = null;
		switch (blockType) {
		case CACTUS:
			skinName = "MHF_Cactus";
			break;
		case CHEST:
			skinName = "MHF_Chest";
			break;
		case MELON_BLOCK:
			skinName = "MHF_Melon";
			break;
		case TNT:
			if (Math.random() > 0.5) {
				skinName = "MHF_TNT";
			} else {
				skinName = "MHF_TNT2";
			}
			break;
		case LOG:
			skinName = "MHF_OakLog";
			break;
		case PUMPKIN:
			skinName = "MHF_Pumpkin";
			break;
		default:
			// TODO .. ?
			/*
			 * Blocks:
				Bonus:
				MHF_ArrowUp
				MHF_ArrowDown
				MHF_ArrowLeft
				MHF_ArrowRight
				MHF_Exclamation
				MHF_Question
			 */
		}
		
		return skinName;
	}
	
	protected String getMobSkin(EntityType mobType)
	{
		String mobSkin = null;
		switch (mobType) {
			case BLAZE:
				mobSkin = "MHF_Blaze";
				break;
			case CAVE_SPIDER:
				mobSkin = "MHF_CaveSpider";
				break;
			case CHICKEN:
				mobSkin = "MHF_Chicken";
				break;
			case COW:
				mobSkin = "MHF_Cow";
				break;
			case ENDERMAN:
				mobSkin = "MHF_Enderman";
				break;
			case GHAST:
				mobSkin = "MHF_Ghast";
				break;
			case IRON_GOLEM:
				mobSkin = "MHF_Golem";
				break;
			case MAGMA_CUBE:
				mobSkin = "MHF_LavaSlime";
				break;
			case MUSHROOM_COW:
				mobSkin = "MHF_MushroomCow";
				break;
			case OCELOT:
				mobSkin = "MHF_Ocelot";
				break;
			case PIG:
				mobSkin = "MHF_Pig";
				break;
			case PIG_ZOMBIE:
				mobSkin = "MHF_PigZombie";
				break;
			case SHEEP:
				mobSkin = "MHF_Sheep";
				break;
			case SLIME:
				mobSkin = "MHF_Slime";
				break;
			case SPIDER:
				mobSkin = "MHF_Spider";
				break;
			case SQUID:
				mobSkin = "MHF_Squid";
				break;
			case VILLAGER:
				mobSkin = "MHF_Villager";
			default:
				// TODO: Find skins for SKELETON, CREEPER and ZOMBIE .. ?
		}
		
		return mobSkin;
	}
	
	protected static Collection<PotionEffect> getPotionEffects(ConfigurationSection parameters)
	{		
		List<PotionEffect> effects = new ArrayList<PotionEffect>();
		PotionEffectType[] effectTypes = PotionEffectType.values();
		for (PotionEffectType effectType : effectTypes) {
			// Why is there a null entry in this list? Maybe a 1.7 bug?
			if (effectType == null) continue;
			
			String parameterName = "effect_" + effectType.getName().toLowerCase();
			if (parameters.contains(parameterName)) {
				String value = parameters.getString(parameterName);
				String[] pieces = value.split(",");
				try {
					Integer ticks = Integer.parseInt(pieces[0]);
					Integer power = 1;
					if (pieces.length > 0) {
						power = Integer.parseInt(pieces[1]);
					}
					PotionEffect effect = new PotionEffect(effectType, ticks, power, true);
					effects.add(effect);
				} catch (Exception ex) {
					Bukkit.getLogger().warning("Error parsing potion effect for " + effectType + ": " + value);
				}
			}
		}
		return effects;
	}

	public boolean isInCircle(int x, int z, int R)
	{
		return ((x * x) +  (z * z) - (R * R)) <= 0;
	}
	
	private boolean canSendMessage()
	{
		if (lastMessageSent == 0) return true;
		int throttle = controller.getMessageThrottle();
		long now = System.currentTimeMillis();
		return (lastMessageSent < now - throttle);
	}
	
	protected Location getEffectLocation()
	{
		return getEyeLocation();
	}
	
	public FireworkEffect getFireworkEffect() {
		return getFireworkEffect(null, null, null, null, null);
	}
	
	public FireworkEffect getFireworkEffect(Color color1, Color color2, org.bukkit.FireworkEffect.Type fireworkType) {
			return getFireworkEffect(color1, color2, fireworkType, null, null);
	}

	public FireworkEffect getFireworkEffect(Color color1, Color color2, org.bukkit.FireworkEffect.Type fireworkType, Boolean flicker, Boolean trail) {
		Color wandColor = mage == null ? null : mage.getEffectColor();
		Random rand = new Random();
		if (wandColor != null) {
			color1 = wandColor;
			color2 = wandColor.mixColors(color1, Color.WHITE);
		} else {
			if (color1 == null) {
				color1 = Color.fromRGB(rand.nextInt(255), rand.nextInt(255), rand.nextInt(255));
			}
			if (color2 == null) {
				color2 = Color.fromRGB(rand.nextInt(255), rand.nextInt(255), rand.nextInt(255));
			}
		}
		if (fireworkType == null) {
			fireworkType = org.bukkit.FireworkEffect.Type.values()[rand.nextInt(org.bukkit.FireworkEffect.Type.values().length)];
		}
		if (flicker == null) {
			flicker = rand.nextBoolean();
		}
		if (trail == null) {
			trail = rand.nextBoolean();
		}
		
		return FireworkEffect.builder().flicker(flicker).withColor(color1).withFade(color2).with(fireworkType).trail(trail).build();
	}
	
	public boolean hasBrushOverride() 
	{
		return false;
	}
	public void checkActiveCosts() {
		if (activeCosts == null) return;
		
		for (CastingCost cost : activeCosts)
		{
			if (!cost.has(this))
			{
				deactivate();
				return;
			}
			
			cost.use(this);
		}
	}
	
	public void checkActiveDuration() {
		if (duration > 0 && lastCast < System.currentTimeMillis() - duration) {
			deactivate();
		}
	}
	
	protected List<CastingCost> parseCosts(ConfigurationSection node) {
		if (node == null) {
			return null;
		}
		List<CastingCost> castingCosts = new ArrayList<CastingCost>();
		Set<String> costKeys = node.getKeys(false);
		for (String key : costKeys)
		{
			castingCosts.add(new CastingCost(key, node.getDouble(key, 1)));
		}
		
		return castingCosts;
	}

	@SuppressWarnings("unchecked")
	protected void loadTemplate(ConfigurationSection node)
	{
		// Get localizations
		name = this.key;
		name = node.getString("name", name);
		name = Messages.get("spells." + key + ".name", name);
		description = node.getString("description", "");
		description = Messages.get("spells." + key + ".description", description);
		usage = Messages.get("spells." + key + ".usage", usage);

		// Load basic properties
		icon = ConfigurationUtils.getMaterialAndData(node, "icon", icon);
		category = node.getString("category", category);
		parameters = node.getConfigurationSection("parameters");
		costs = parseCosts(node.getConfigurationSection("costs"));
		activeCosts = parseCosts(node.getConfigurationSection("active_costs"));
		
		// Load effects ... Config API is kind of ugly here, and I'm not actually
		// sure this is valid YML... :\
		effects.clear();
		if (node.contains("effects")) {
			ConfigurationSection effectsNode = node.getConfigurationSection("effects");
			for (SpellResult resultType : SpellResult.values()) {
				String typeName = resultType.name().toLowerCase();
				if (effectsNode.contains(typeName)) {
					Collection<ConfigurationSection> effectNodes = ConfigurationUtils.getNodeList(effectsNode, typeName);
			        if (effectNodes != null) 
			        {
			        	List<EffectPlayer> players = new ArrayList<EffectPlayer>();
			            for (ConfigurationSection effectValues : effectNodes)
			            {
		                    if (effectValues.contains("class")) {
		                    	String effectClass = effectValues.getString("class");
			                    try {
			                    	Class<?> genericClass = Class.forName(EFFECT_BUILTIN_CLASSPATH + "." + effectClass);
			                    	if (!EffectPlayer.class.isAssignableFrom(genericClass)) {
			                    		throw new Exception("Must extend EffectPlayer");
			                    	}
			                    	
									Class<? extends EffectPlayer> playerClass = (Class<? extends EffectPlayer>)genericClass;
				                    EffectPlayer player = playerClass.newInstance();
				                    player.load(controller.getPlugin(), effectValues);
				                    players.add(player);
			                    } catch (Exception ex) {
			                    	ex.printStackTrace();
			                    	controller.getLogger().info("Error creating effect class: " + effectClass + " " + ex.getMessage());
			                    }
		                    }
			            }
			            
			            effects.put(resultType, players);
			        }
				}
			}
		}
		
		// Populate default effects
		initializeDefaultSound(SpellResult.FAIL, Sound.NOTE_BASS_DRUM, 0.9f, 1.2f);
		initializeDefaultSound(SpellResult.INSUFFICIENT_RESOURCES, Sound.NOTE_BASS, 1.0f, 1.2f);
		initializeDefaultSound(SpellResult.INSUFFICIENT_PERMISSION, Sound.NOTE_BASS, 1.1f, 1.5f);
		initializeDefaultSound(SpellResult.COOLDOWN, Sound.NOTE_SNARE_DRUM, 1.1f, 0.9f);
		initializeDefaultSound(SpellResult.NO_TARGET, Sound.NOTE_STICKS, 1.1f, 0.9f);
		
		if (!effects.containsKey(SpellResult.TARGET_SELECTED)) {
			List<EffectPlayer> effectList = new ArrayList<EffectPlayer>();
			EffectPlayer targetHighlight = new EffectSingle(controller.getPlugin());
			targetHighlight.setSound(Sound.ANVIL_USE);
			targetHighlight.setParticleType(ParticleType.HAPPY_VILLAGER);
			targetHighlight.setLocationType("target");
			targetHighlight.setOffset(0.5f, 0.5f, 0.5f);
			effectList.add(targetHighlight);
			EffectPlayer trail = new EffectTrail(controller.getPlugin());
			trail.setParticleType(ParticleType.WATER_DRIPPING);
			effectList.add(trail);
			effects.put(SpellResult.TARGET_SELECTED, effectList);
		}
		
		if (!effects.containsKey(SpellResult.COST_FREE) && effects.containsKey(SpellResult.CAST)) {
			effects.put(SpellResult.COST_FREE, effects.get(SpellResult.CAST));
		}
	}
	
	protected void initializeDefaultSound(SpellResult result, Sound sound, float volume, float pitch) {
		if (effects.containsKey(result)) return;
		
		EffectPlayer defaultEffect = new EffectSingle(controller.getPlugin());
		defaultEffect.setSound(sound, volume, pitch);
		List<EffectPlayer> effectList = new ArrayList<EffectPlayer>();
		effectList.add(defaultEffect);
		effects.put(result, effectList);
	}

	public boolean isMatch(String spell, String[] params)
	{
		if (params == null) params = new String[0];
		return (key.equalsIgnoreCase(spell) && parameters.equals(params));
	}

	public int compareTo(com.elmakers.mine.bukkit.api.spell.SpellTemplate other)
	{
		return name.compareTo(other.getName());
	}
	
	protected void preCast()
	{
		
	}

	protected void reset()
	{
		Location mageLocation = mage != null ? mage.getLocation() : null;
		
		// Kind of a hack, but assume the default location has no direction.
		if (this.location != null && mageLocation != null) {
			this.location.setPitch(mageLocation.getPitch());
			this.location.setYaw(mageLocation.getYaw());
		}
	}

	public boolean cast(String[] extraParameters, Location defaultLocation)
	{
		this.location = defaultLocation;
		this.reset();
		
		if (this.parameters == null) {
			this.parameters = new MemoryConfiguration();
		}
		final ConfigurationSection parameters = new MemoryConfiguration();
		ConfigurationUtils.addConfigurations(parameters, this.parameters);
		ConfigurationUtils.addParameters(extraParameters, parameters);
		processParameters(parameters);
		
		this.preCast();
		
		// Check cooldowns
		cooldown = parameters.getInt("cooldown", cooldown);
		cooldown = parameters.getInt("cool", cooldown);
		
		long currentTime = System.currentTimeMillis();
		if (!mage.isCooldownFree()) {
			double cooldownReduction = mage.getCooldownReduction() + this.cooldownReduction;
			if (cooldownReduction < 1 && !isActive && cooldown > 0) {
				int reducedCooldown = (int)Math.ceil((1.0f - cooldownReduction) * cooldown);
				if (lastCast != 0 && lastCast > currentTime - reducedCooldown)
				{
					long seconds = (lastCast - (currentTime - reducedCooldown)) / 1000;
					if (seconds > 60 * 60 ) {
						long hours = seconds / (60 * 60);
						sendMessage(Messages.get("cooldown.wait_hours").replace("$hours", ((Long)hours).toString()));					
					} else if (seconds > 60) {
						long minutes = seconds / 60;
						sendMessage(Messages.get("cooldown.wait_minutes").replace("$minutes", ((Long)minutes).toString()));					
					} else if (seconds > 1) {
						sendMessage(Messages.get("cooldown.wait_seconds").replace("$seconds", ((Long)seconds).toString()));
					} else {
						sendMessage(Messages.get("cooldown.wait_moment"));
					}
					processResult(SpellResult.COOLDOWN);
					return false;
				}
			}
		}

		if (!mage.isCostFree())
		{
			if (costs != null && !isActive)
			{
				for (CastingCost cost : costs)
				{
					if (!cost.has(this))
					{
						String baseMessage = Messages.get("costs.insufficient_resources");
						String costDescription = cost.getDescription(mage);
						sendMessage(baseMessage.replace("$cost", costDescription));
						processResult(SpellResult.INSUFFICIENT_RESOURCES);
						return false;
					}
				}
			}
		}
		
		if (!canCast()) {
			processResult(SpellResult.INSUFFICIENT_PERMISSION);
			return false;
		}
		
		return finalizeCast(parameters);
	}
	
	protected boolean canCast() {
		return true;
	}
	
	protected void onBackfire() {
		
	}
	
	protected boolean finalizeCast(ConfigurationSection parameters) {
		SpellResult result = null;
		if (!mage.isSuperPowered()) {
			if (backfireChance > 0 && Math.random() < backfireChance) {
				onBackfire();
				onCast(parameters);
				result = SpellResult.BACKFIRE;
			} else if (fizzleChance > 0 && Math.random() < fizzleChance) {
				result = SpellResult.FIZZLE;
			}
		}
		
		if (result == null) {
			result = onCast(parameters);
		}
		processResult(result);
		
		if (result.isSuccess()) {
			lastCast = System.currentTimeMillis();
			if (costs != null && !mage.isCostFree()) {
				for (CastingCost cost : costs)
				{
					cost.use(this);
				}
			}
			castCount++;
		}
		
		return result.isSuccess();
	}
	
	public String getMessage(String messageKey) {
		return getMessage(messageKey, "");
	}
	
	public String getMessage(String messageKey, String def) {
		String message = Messages.get("spells.default." + messageKey, def);
		message = Messages.get("spells." + key + "." + messageKey, message);
		if (message == null) message = "";
		
		// Escape some common parameters
		String playerName = mage.getName();
		message = message.replace("$player", playerName);
		
		String materialName = getDisplayMaterialName();
		message = message.replace("$material", materialName);
		
		return message;
	}

	protected String getDisplayMaterialName()
	{
		return "None";
	}
	
	protected void processResult(SpellResult result) {
		if (mage != null) {
			mage.onCast(this, result);
		}
		
		// Show messaging
		if (result == SpellResult.CAST) {
			String message = getMessage(result.name().toLowerCase());
			Player player = mage.getPlayer();
			Entity targetEntity = getTargetEntity();
			if (targetEntity == player) {
				message = getMessage("cast_self", message);
			} else if (targetEntity instanceof Player) {
				message = getMessage("cast_player", message);
				String playerMessage = getMessage("cast_player_message");
				if (playerMessage.length() > 0) {
					playerMessage = playerMessage.replace("$spell", getName());
					Player targetPlayer = (Player)targetEntity;
					Mage targetMage = controller.getMage(targetPlayer);
					targetMage.sendMessage(playerMessage);
				}
			} else if (targetEntity instanceof LivingEntity) {
				message = getMessage("cast_livingentity", message);
			} else if (targetEntity instanceof Entity) {
				message = getMessage("cast_entity", message);
			}
			castMessage(message);
		} else {
			sendMessage(getMessage(result.name().toLowerCase()));
		}
		
		// Play effects
		Location mageLocation = getEffectLocation();
		if (effects.containsKey(result) && mageLocation != null) {
			Location targetLocation = getTargetLocation();
			List<EffectPlayer> resultEffects = effects.get(result);
			for (EffectPlayer player : resultEffects) {
				// Set material and color
				player.setMaterial(getEffectMaterial());
				player.setColor(mage.getEffectColor());
				player.start(mageLocation, targetLocation);
			}
		}
	}
	
	public Location getTargetLocation() {
		return null;
	}
	
	public Entity getTargetEntity() {
		return null;
	}
	
	public com.elmakers.mine.bukkit.api.block.MaterialAndData getEffectMaterial()
	{
		return new MaterialAndData(DEFAULT_EFFECT_MATERIAL);
	}

	protected void processParameters(ConfigurationSection parameters) {
		duration = parameters.getInt("duration", duration);
		
		fizzleChance = (float)parameters.getDouble("fizzle_chance", fizzleChance);
		backfireChance = (float)parameters.getDouble("backfire_chance", backfireChance);
	
		Location defaultLocation = location == null ? mage.getLocation() : location;
		Location locationOverride = ConfigurationUtils.overrideLocation(parameters, "p", defaultLocation, controller.canCreateWorlds());
		if (locationOverride != null) {
			location = locationOverride;
		}
		costReduction = (float)parameters.getDouble("cost_reduction", 0);
		cooldownReduction = (float)parameters.getDouble("cooldown_reduction", 0);
	
		if (parameters.contains("prevent_passthrough")) {
			preventPassThroughMaterials = controller.getMaterialSet(parameters.getString("prevent_passthrough"));
		} else {
			preventPassThroughMaterials = controller.getMaterialSet("indestructible");
		}
	}
	
	
	public String getPermissionNode()
	{
		return "Magic.cast." + key;
	}

	public boolean hasSpellPermission(CommandSender sender)
	{
		if (sender == null) return true;

		return controller.hasPermission(sender, getPermissionNode(), true);
	}
	
	/**
	 * Called when a material selection spell is cancelled mid-selection.
	 */
	public boolean onCancel()
	{
		return false;
	}

	/**
	 * Listener method, called on player quit for registered spells.
	 * 
	 * @param event The player who just quit
	 * @see MagicController#registerEvent(SpellEventType, DeleteSpell)
	 */
	public void onPlayerQuit(PlayerQuitEvent event)
	{

	}

	/**
	 * Listener method, called on player move for registered spells.
	 * 
	 * @param event The original entity death event
	 * @see MagicController#registerEvent(SpellEventType, DeleteSpell)
	 */
	public void onPlayerDeath(EntityDeathEvent event)
	{

	}

	public void onPlayerDamage(EntityDamageEvent event)
	{

	}
	
	/**
	 * Used internally to initialize the Spell, do not call.
	 * 
	 * @param instance The spells instance
	 */
	public void initialize(MageController instance)
	{
		this.controller = instance;
	}
	
	public long getCastCount()
	{
		return castCount;
	}
	
	public void onActivate() {
		
	}
	
	public void onDeactivate() {

	}
	
	/**
	 * Called on player data load.
	 */
	public void onLoad(ConfigurationSection node)
	{
		
	}

	/**
	 * Called on player data save.
	 * 
	 * @param node The configuration node to load data from.
	 */
	public void onSave(ConfigurationSection node)
	{

	}
	
	//
	// Cloneable implementation
	//
	
	@Override
	public Object clone()
	{
		try
		{
			return super.clone();
		}
		catch (CloneNotSupportedException ex)
		{
			return null;
		}
	}
	
	//
	// CostReducer Implementation
	//
	
	@Override
	public float getCostReduction()
	{
		return costReduction + mage.getCostReduction();
	}
	
	@Override
	public boolean usesMana() 
	{
		return mage.usesMana();
	}
	
	//
	// Public API Implementation
	//
	
	@Override
	public com.elmakers.mine.bukkit.api.spell.Spell createSpell()
	{
		return (Spell)this.clone();
	}
	
	@Override
	public boolean cast()
	{
		return cast(new String[0], null);
	}
	
	@Override
	public boolean cast(String[] extraParameters)
	{
		return cast(extraParameters, null);
	}

	@Override
	public final String getKey()
	{
		return key;
	}

	@Override
	public final String getName()
	{
		return name;
	}

	@Override
	public final com.elmakers.mine.bukkit.api.block.MaterialAndData getIcon()
	{
		return icon;
	}

	@Override
	public final String getDescription()
	{
		return description;
	}

	@Override
	public final String getUsage()
	{
		return usage;
	}

	@Override
	public final String getCategory()
	{
		return category;
	}
	
	@Override
	public Collection<com.elmakers.mine.bukkit.api.effect.EffectPlayer> getEffects(SpellResult result) {
		List<com.elmakers.mine.bukkit.api.effect.EffectPlayer> effectList = new ArrayList<com.elmakers.mine.bukkit.api.effect.EffectPlayer>(effects.get(result));
		return effectList;
	}
	
	@Override
	public Collection<com.elmakers.mine.bukkit.api.spell.CastingCost> getCosts() {
		if (costs == null) return null;
		List<com.elmakers.mine.bukkit.api.spell.CastingCost> copy = new ArrayList<com.elmakers.mine.bukkit.api.spell.CastingCost>();
		copy.addAll(costs);
		return copy;
	}
	
	@Override
	public Collection<com.elmakers.mine.bukkit.api.spell.CastingCost> getActiveCosts() {
		if (activeCosts == null) return null;
		List<com.elmakers.mine.bukkit.api.spell.CastingCost> copy = new ArrayList<com.elmakers.mine.bukkit.api.spell.CastingCost>();
		copy.addAll(activeCosts);
		return copy;
	}
	
	@Override
	public void getParameters(Collection<String> parameters)
	{
		parameters.addAll(Arrays.asList(COMMON_PARAMETERS));
	}
	
	@Override
	public void getParameterOptions(Collection<String> examples, String parameterKey)
	{
		if (parameterKey.equals("duration")) {
			examples.addAll(Arrays.asList(EXAMPLE_DURATIONS));
		} else if (parameterKey.equals("range")) {
			examples.addAll(Arrays.asList(EXAMPLE_SIZES));
		} else if (parameterKey.equals("transparent")) {
			examples.addAll(controller.getMaterialSets());
		} else if (parameterKey.equals("player")) {
			examples.addAll(controller.getPlayerNames());
		} else if (parameterKey.equals("target")) {
			TargetType[] targetTypes = TargetType.values();
			for (TargetType targetType : targetTypes) {
				examples.add(targetType.name().toLowerCase());
			}
		} else if (parameterKey.equals("target")) {
			TargetType[] targetTypes = TargetType.values();
			for (TargetType targetType : targetTypes) {
				examples.add(targetType.name().toLowerCase());
			}
		} else if (parameterKey.equals("target_type")) {
			EntityType[] entityTypes = EntityType.values();
			for (EntityType entityType : entityTypes) {
				examples.add(entityType.name().toLowerCase());
			}
		} else if (booleanParameterMap.contains(parameterKey)) {
			examples.addAll(Arrays.asList(EXAMPLE_BOOLEANS));
		} else if (vectorParameterMap.contains(parameterKey)) {
			examples.addAll(Arrays.asList(EXAMPLE_VECTOR_COMPONENTS));
		} else if (worldParameterMap.contains(parameterKey)) {
			List<World> worlds = Bukkit.getWorlds();
			for (World world : worlds) {
				examples.add(world.getName());
			}
		} else if (percentageParameterMap.contains(parameterKey)) {
			examples.addAll(Arrays.asList(EXAMPLE_PERCENTAGES));
		} 
	}
	
	@Override
	public long getCooldown()
	{
		return cooldown;
	}
	
	@Override
	public long getDuration()
	{
		return duration;
	}

	@Override
	public void setMage(Mage mage)
	{
		this.mage = mage;
	}
	
	@Override
	public boolean cancel()
	{
		boolean cancelled = onCancel();
		if (cancelled) {
			sendMessage(getMessage("cancel"));
		}
		return cancelled;
	}
	
	@Override
	public void activate() {
		if (!isActive) {
			isActive = true;
			onActivate();
			
			mage.activateSpell(this);
		}
	}
	
	@Override
	public void deactivate() {
		if (isActive) {
			isActive = false;
			onDeactivate();
			
			mage.deactivateSpell(this);
			sendMessage(getMessage("deactivate"));
		}
	}
	
	@Override
	public Mage getMage() {
		return mage;
	}
	
	@Override
	public void load(ConfigurationSection node) {
		try {
			castCount = node.getLong("cast_count", 0);
			lastCast = node.getLong("last_cast", 0);
			onLoad(node);
		} catch (Exception ex) {
			controller.getPlugin().getLogger().warning("Failed to load data for spell " + name + ": " + ex.getMessage());
		}
	}
	
	@Override
	public void save(ConfigurationSection node) {
		try {
			node.set("cast_count", castCount);
			node.set("last_cast", lastCast);
			onSave(node);
		} catch (Exception ex) {
			controller.getPlugin().getLogger().warning("Failed to save data for spell " + name);
			ex.printStackTrace();
		}
	}

	@Override
	public void loadTemplate(String key, ConfigurationSection node)
	{
		this.key = key;
		this.loadTemplate(node);
	}
	
	@Override
	public void tick()
	{
		checkActiveDuration();
		checkActiveCosts();
	}
	
	//
	// Spell abstract interface
	//

	/**
	 * Called when this spell is cast.
	 * 
	 * This is where you do your work!
	 * 
	 * If parameters were passed to this spell, either via a variant or the command line,
	 * they will be passed in here.
	 * 
	 * @param parameters Any parameters that were passed to this spell
	 * @return true if the spell worked, false if it failed
	 */
	public abstract SpellResult onCast(ConfigurationSection parameters);
}