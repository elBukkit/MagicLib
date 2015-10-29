package com.elmakers.mine.bukkit.utility;

import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.block.UndoList;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.spell.TargetType;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Targeting {
    private TargetingResult                     result                  = TargetingResult.NONE;
    private Location                            source                  = null;

    private Target								target					= null;
    private List<Target>                        targets                 = null;

    private TargetType							targetType				= TargetType.NONE;
    private BlockIterator                       blockIterator           = null;
    private Block                               currentBlock            = null;
    private	Block								previousBlock           = null;
    private	Block								previousPreviousBlock   = null;

    private Vector                              targetLocationOffset;
    private Vector								targetDirectionOverride;
    private String								targetLocationWorldName;

    protected float                             distanceWeight          = 1;
    protected float                             fovWeight               = 4;
    protected int                               npcWeight               = -1;
    protected int                               mageWeight              = 5;
    protected int                               playerWeight            = 4;
    protected int                               livingEntityWeight      = 3;

    private boolean                             ignoreBlocks            = false;

    private double                              hitboxPadding           = 0;
    private double                              rangeQueryPadding       = 1;
    private boolean                             useHitbox               = true;
    private double                              fov                     = 0.3;
    private double                              closeRange              = 0;
    private double                              closeFOV                = 0;
    private double                              yOffset                 = 0;
    private boolean                             targetSpaceRequired     = false;
    private int                                 targetMinOffset         = 0;

    public enum TargetingResult {
        NONE,
        BLOCK,
        ENTITY,
        MISS
    };

    public void reset() {
        result = TargetingResult.NONE;
        source = null;
        target = null;
        targets = null;
        blockIterator = null;
        currentBlock = null;
        previousBlock = null;
        previousPreviousBlock = null;
        targetSpaceRequired = false;
        targetMinOffset = 0;
        yOffset = 0;
    }

    protected boolean initializeBlockIterator(Location location, double range) {
        if (blockIterator != null) {
            return true;
        }
        if (location.getBlockY() < 0) {
            location = location.clone();
            location.setY(0);
        }
        int maxHeight = CompatibilityUtils.getMaxHeight(location.getWorld());
        if (location.getBlockY() > maxHeight) {
            location = location.clone();
            location.setY(maxHeight);
        }

        try {
            blockIterator = new BlockIterator(location, yOffset, (int)Math.ceil(range));
        } catch (Exception ex) {
            if (Target.DEBUG_TARGETING) {
                org.bukkit.Bukkit.getLogger().warning("Exception creating BlockIterator");
                ex.printStackTrace();
            }
            // This seems to happen randomly, like when you use the same target.
            // Very annoying, and I now kind of regret switching to BlockIterator.
            // At any rate, we're going to just re-use the last target block and
            // cross our fingers!
            return false;
        }

        return true;
    }

    public Target getOrCreateTarget(Location defaultLocation) {
        if (target == null) {
            target = new Target(defaultLocation);
        }
        return target;
    }

    public Target getTarget() {
        return target;
    }

    public boolean hasTarget() {
        return target != null;
    }

    public void setTargetSpaceRequired(boolean required) {
        targetSpaceRequired = required;
    }

    public void setTargetMinOffset(int offset) {
        targetMinOffset = offset;
    }

    public void targetBlock(Location source, Block block) {
        target = new Target(source, block, useHitbox, hitboxPadding);
    }

    public void setYOffset(int offset) {
        yOffset = offset;
    }

    /**
     * Move "steps" forward along line of vision and returns the block there
     *
     * @return The block at the new location
     */
    protected Block getNextBlock()
    {
        previousPreviousBlock = previousBlock;
        previousBlock = currentBlock;
        if (blockIterator == null || !blockIterator.hasNext()) {
            currentBlock = null;
        } else {
            currentBlock = blockIterator.next();
        }
        return currentBlock;
    }

    /**
     * Returns the current block along the line of vision
     *
     * @return The block
     */
    public Block getCurBlock()
    {
        return currentBlock;
    }

    /**
     * Returns the previous block along the line of vision
     *
     * @return The block
     */
    public Block getPreviousBlock()
    {
        return previousBlock;
    }

    public void setFOV(double fov) {
        this.fov = fov;
    }

    public void setCloseRange(double closeRange) {
        this.closeRange = closeRange;
    }

    public void setCloseFOV(double closeFOV) {
        this.closeFOV = closeFOV;
    }

    public void setUseHitbox(boolean useHitbox) {
        this.useHitbox = useHitbox;
    }

    public TargetType getTargetType()
    {
        return targetType;
    }

    public void setTargetType(TargetType type) {
        targetType = type;
    }

    public void start(Location source) {
        reset();
        this.source = source.clone();
    }

    public Target target(CastContext context, double range)
    {
        target = findTarget(context, range);

        if (targetLocationOffset != null) {
            target.add(targetLocationOffset);
        }
        if (targetDirectionOverride != null) {
            target.setDirection(targetDirectionOverride);
        }
        if (targetLocationWorldName != null && targetLocationWorldName.length() > 0) {
            Location location = target.getLocation();
            if (location != null) {
                World targetWorld = location.getWorld();
                target.setWorld(ConfigurationUtils.overrideWorld(targetLocationWorldName, targetWorld, context.getController().canCreateWorlds()));
            }
        }

        return target;
    }

    /**
     * Returns the block at the cursor, or null if out of range
     *
     * @return The target block
     */
    public Target findTarget(CastContext context, double range)
    {
        if (targetType == TargetType.NONE) {
            return new Target(source);
        }
        boolean isBlock = targetType == TargetType.BLOCK || targetType == TargetType.SELECT;

        Mage mage = context.getMage();
        final Entity mageEntity = mage.getEntity();
        if (targetType == TargetType.SELF && mageEntity != null) {
            result = TargetingResult.ENTITY;
            return new Target(source, mageEntity);
        }

        CommandSender sender = mage.getCommandSender();
        if (targetType == TargetType.SELF && mageEntity == null && sender != null && (sender instanceof BlockCommandSender)) {
            BlockCommandSender commandBlock = (BlockCommandSender)mage.getCommandSender();
            return new Target(commandBlock.getBlock().getLocation(), commandBlock.getBlock());
        }

        if (targetType == TargetType.SELF && source != null) {
            return new Target(source, source.getBlock());
        }

        if (targetType == TargetType.SELF) {
            return new Target(source);
        }

        Block block = null;
        if (!ignoreBlocks) {
            findTargetBlock(context, range);
            block = currentBlock;
        }

        if (isBlock) {
            return new Target(source, block, useHitbox, hitboxPadding);
        }

        Target targetBlock = block == null ? null : new Target(source, block, useHitbox, hitboxPadding);

        // Don't target entities beyond the block we just hit
        if (targetBlock != null && source != null && source.getWorld().equals(block.getWorld()))
        {
            range = Math.min(range, source.distance(targetBlock.getLocation()));
        }

        // Pick the closest candidate entity
        Target entityTarget = null;
        List<Target> scored = getAllTargetEntities(context, range);
        if (scored.size() > 0) {
            entityTarget = scored.get(0);
        }

        // Don't allow targeting entities in an area you couldn't cast the spell in
        if (entityTarget != null && !context.canCast(entityTarget.getLocation())) {
            entityTarget = null;
        }
        if (targetBlock != null && !context.canCast(targetBlock.getLocation())) {
            targetBlock = null;
        }

        if (targetType == TargetType.OTHER_ENTITY && entityTarget == null) {
            result = TargetingResult.ENTITY;
            return new Target(source);
        }

        if (targetType == TargetType.ANY_ENTITY && entityTarget == null) {
            result = TargetingResult.ENTITY;
            return new Target(source, mageEntity);
        }

        if (entityTarget == null && targetType == TargetType.ANY && mageEntity != null) {
            result = TargetingResult.ENTITY;
            return new Target(source, mageEntity, targetBlock == null ? null : targetBlock.getBlock());
        }

        if (targetBlock != null && entityTarget != null) {
            if (targetBlock.getDistanceSquared() < entityTarget.getDistanceSquared()) {
                entityTarget = null;
            } else {
                targetBlock = null;
            }
        }

        if (entityTarget != null) {
            result = TargetingResult.ENTITY;
            return entityTarget;
        } else if (targetBlock != null) {
            return targetBlock;
        }

        return new Target(source);
    }

    protected void findTargetBlock(CastContext context, double range)
    {
        if (source == null)
        {
            source = context.getEyeLocation();
        }
        if (source == null)
        {
            return;
        }

        // Pre-check for no block movement
        Location targetLocation = source.clone().add(source.getDirection().multiply(range));
        if (targetLocation.getBlockX() == source.getBlockX() &&
            targetLocation.getBlockY() == source.getBlockY() &&
            targetLocation.getBlockZ() == source.getBlockZ()) {

            currentBlock = source.getBlock();
            if (context.isTargetable(currentBlock)) {
                result = TargetingResult.BLOCK;
            } else {
                result = TargetingResult.MISS;
            }
            return;
        }

        if (!initializeBlockIterator(source, range))
        {
            return;
        }

        Block block = getNextBlock();
        result = TargetingResult.BLOCK;
        while (block != null)
        {
            if (targetMinOffset <= 0) {
                if (targetSpaceRequired) {
                    if (!context.allowPassThrough(block.getType())) {
                        break;
                    }
                    if (context.isOkToStandIn(block.getType()) && context.isOkToStandIn(block.getRelative(BlockFace.UP).getType())) {
                        break;
                    }
                } else if (context.isTargetable(block)) {
                    break;
                }
            } else {
                targetMinOffset--;
            }
            block = getNextBlock();
        }
        if (block == null) {
            result = TargetingResult.MISS;
            currentBlock = previousBlock;
            previousBlock = previousPreviousBlock;
        }
    }

    public List<Target> getAllTargetEntities(CastContext context, double range) {
        Entity sourceEntity = context.getEntity();
        Mage mage = context.getMage();

        if (targets != null) {
            return targets;
        }
        targets = new ArrayList<Target>();

        // A fuzzy optimization range-check. A hard range limit is enforced in the final target consolidator
        double rangeSquaredPadded = (range + 1) * (range + 1);

        List<Entity> entities = null;
        range = Math.min(range + hitboxPadding + rangeQueryPadding, CompatibilityUtils.MAX_ENTITY_RANGE);
        if (source == null && sourceEntity != null) {
            entities = sourceEntity.getNearbyEntities(range, range, range);
            if (sourceEntity instanceof LivingEntity) {
                source = ((LivingEntity)sourceEntity).getEyeLocation();
            } else {
                source = sourceEntity.getLocation();
            }
        } else if (source != null) {
            entities = CompatibilityUtils.getNearbyEntities(source, range, range, range);
        }

        if (mage != null && mage.getDebugLevel() > 8)
        {
            mage.sendDebugMessage(ChatColor.GREEN + "Targeting from " + ChatColor.GRAY + source.getBlockX() +
                    ChatColor.DARK_GRAY + ","  + ChatColor.GRAY + source.getBlockY() +
                    ChatColor.DARK_GRAY + "," + ChatColor.GRAY + source.getBlockZ() +
                    ChatColor.DARK_GREEN + " with range of " + ChatColor.GREEN + range);
        }

        if (entities == null) return targets;
        for (Entity entity : entities)
        {
            if (sourceEntity != null && entity.equals(sourceEntity) && !context.getTargetsCaster()) continue;
            Location entityLocation = entity instanceof LivingEntity ? ((LivingEntity)entity).getEyeLocation() : entity.getLocation();
            if (!entityLocation.getWorld().equals(source.getWorld())) continue;
            if (entityLocation.distanceSquared(source) > rangeSquaredPadded) continue;
            if (!context.canTarget(entity)) continue;

            Target newScore = null;
            if (useHitbox) {
                newScore = new Target(source, entity, (int)Math.ceil(range), useHitbox, hitboxPadding);
            } else {
                newScore = new Target(source, entity, (int)Math.ceil(range), fov, closeRange, closeFOV,
                        distanceWeight, fovWeight, mageWeight, npcWeight, playerWeight, livingEntityWeight);
            }
            if (newScore.getScore() > 0)
            {
                if (mage != null && mage.getDebugLevel() > 5)
                {
                    mage.sendDebugMessage(ChatColor.DARK_GREEN + "Target " + ChatColor.GREEN + entity.getType() + ChatColor.DARK_GREEN + ": " + ChatColor.YELLOW + newScore.getScore());
                }

                targets.add(newScore);
            }
        }

        Collections.sort(targets);
        return targets;
    }

    public void parseTargetType(String targetTypeName) {
        targetType = TargetType.NONE;
        if (targetTypeName != null) {
            try {
                targetType = TargetType.valueOf(targetTypeName.toUpperCase());
            } catch (Exception ex) {
                targetType = TargetType.NONE;
            }
        }
    }

    public void processParameters(ConfigurationSection parameters) {
        parseTargetType(parameters.getString("target"));
        useHitbox = parameters.getBoolean("hitbox", !parameters.contains("fov"));
        hitboxPadding = parameters.getDouble("hitbox_size", 0);
        rangeQueryPadding = parameters.getDouble("range_padding", 1);
        fov = parameters.getDouble("fov", 0.3);
        closeRange = parameters.getDouble("close_range", 1);
        closeFOV = parameters.getDouble("close_fov", 0.5);

        distanceWeight = (float)parameters.getDouble("distance_weight", 1);
        fovWeight = (float)parameters.getDouble("fov_weight", 4);
        npcWeight = parameters.getInt("npc_weight", -1);
        playerWeight = parameters.getInt("player_weight", 4);
        livingEntityWeight = parameters.getInt("entity_weight", 3);

        targetMinOffset = parameters.getInt("target_min_offset", 0);
        targetMinOffset = parameters.getInt("tmo", targetMinOffset);

        ignoreBlocks = parameters.getBoolean("ignore_blocks", false);

        targetLocationOffset = null;
        targetDirectionOverride = null;

        Double otxValue = ConfigurationUtils.getDouble(parameters, "otx", null);
        Double otyValue = ConfigurationUtils.getDouble(parameters, "oty", null);
        Double otzValue = ConfigurationUtils.getDouble(parameters, "otz", null);
        if (otxValue != null || otzValue != null || otyValue != null) {
            targetLocationOffset = new Vector(
                    (otxValue == null ? 0 : otxValue),
                    (otyValue == null ? 0 : otyValue),
                    (otzValue == null ? 0 : otzValue));
        }
        targetLocationWorldName = parameters.getString("otworld");

        Double tdxValue = ConfigurationUtils.getDouble(parameters, "otdx", null);
        Double tdyValue = ConfigurationUtils.getDouble(parameters, "otdy", null);
        Double tdzValue = ConfigurationUtils.getDouble(parameters, "otdz", null);
        if (tdxValue != null || tdzValue != null || tdyValue != null) {
            targetDirectionOverride = new Vector(
                    (tdxValue == null ? 0 : tdxValue),
                    (tdyValue == null ? 0 : tdyValue),
                    (tdzValue == null ? 0 : tdzValue));
        }
    }

    public TargetingResult getResult() {
        return result;
    }

    public void getTargetEntities(CastContext context, double range, int targetCount, Collection<WeakReference<Entity>> entities)
    {
        List<Target> candidates = getAllTargetEntities(context, range);
        if (targetCount < 0) {
            targetCount = entities.size();
        }

        for (int i = 0; i < targetCount && i < candidates.size(); i++) {
            Target target = candidates.get(i);
            entities.add(new WeakReference<Entity>(target.getEntity()));
        }
    }

    public int breakBlock(CastContext context, Block block, double amount) {
        if (amount <= 0) return 0;
        Double breakableAmount = context.getBreakable(block);
        if (breakableAmount == null) return 0;

        double breakable = (int)(amount > 1 ? amount : (context.getRandom().nextDouble() < amount ? 1 : 0));
        if (breakable <= 0) return 0;

        breakable = (int)Math.ceil(breakableAmount + breakable - 1);

        // Play break FX
        Location blockLocation = block.getLocation();
        Location effectLocation = blockLocation.add(0.5, 0.5, 0.5);
        context.playEffects("break", 1, context.getLocation(), null, effectLocation, null);

        // TODO: Re-examime this?
        UndoList undoList = com.elmakers.mine.bukkit.block.UndoList.getUndoList(blockLocation);
        if (undoList != null) {
            undoList.add(block);
        }

        context.clearBreakable(block);
        context.clearReflective(block);
        block.setType(Material.AIR);

        int broken = 1;
        if (breakable > broken) {
            broken += breakBlock(context, block.getRelative(BlockFace.UP), breakable - broken);
            broken += breakBlock(context, block.getRelative(BlockFace.DOWN),  breakable - broken);
            broken += breakBlock(context, block.getRelative(BlockFace.EAST),  breakable - broken);
            broken += breakBlock(context, block.getRelative(BlockFace.WEST),  breakable - broken);
            broken += breakBlock(context, block.getRelative(BlockFace.NORTH),  breakable - broken);
            broken += breakBlock(context, block.getRelative(BlockFace.SOUTH),  breakable - broken);
        }

        return broken;
    }
}