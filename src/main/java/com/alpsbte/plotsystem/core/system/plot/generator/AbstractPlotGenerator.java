/*
 * The MIT License (MIT)
 *
 *  Copyright © 2021-2022, Alps BTE <bte.atchli@gmail.com>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.alpsbte.plotsystem.core.system.plot.generator;

import com.alpsbte.plotsystem.PlotSystem;
import com.alpsbte.plotsystem.commands.BaseCommand;
import com.alpsbte.plotsystem.core.config.ConfigPaths;
import com.alpsbte.plotsystem.core.system.Builder;
import com.alpsbte.plotsystem.core.system.plot.Plot;
import com.alpsbte.plotsystem.core.system.plot.PlotHandler;
import com.alpsbte.plotsystem.core.system.plot.PlotManager;
import com.alpsbte.plotsystem.utils.Utils;
import com.alpsbte.plotsystem.utils.enums.Status;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;

public abstract class AbstractPlotGenerator {

    private static final MVWorldManager worldManager = PlotSystem.DependencyManager.getMultiverseCore().getMVWorldManager();
    private WorldCreator worldCreator;

    private final Plot plot;
    private final Builder builder;

    /**
     * @param plot - plot which should be generated
     * @param builder - builder of the plot
     */
    public AbstractPlotGenerator(@NotNull Plot plot, @NotNull Builder builder) {
        this.plot = plot;
        this.builder = builder;

        if (init()) {
            Exception exception = null;
            try {
                generateWorld();
                generateOutlines(plot.getOutlinesSchematic());
                createMultiverseWorld();
                configureWorld(worldManager.getMVWorld(plot.getWorld().getBukkitWorld()));
                createProtection();
            } catch (Exception ex) {
                exception = ex;
            }

            try {
                this.onComplete(exception != null);
            } catch (SQLException ex) {
                exception = ex;
            }

            if (exception != null) {
                if (worldManager.isMVWorld(plot.getWorld().getWorldName())) PlotHandler.abandonPlot(plot);
                onException(exception);
            }
        }
    }

    /**
     * Executed before plot generation
     * @return true if initialization was successful
     */
    protected abstract boolean init();

    /**
     * Generates plot world
     */
    protected void generateWorld() {
        if (getPlot().getWorld().isWorldGenerated()) plot.getWorld().deleteWorld();

        worldCreator = new WorldCreator(plot.getWorld().getWorldName());
        worldCreator.environment(org.bukkit.World.Environment.NORMAL);
        worldCreator.type(WorldType.FLAT);
        worldCreator.generator("VoidGen");
        //worldCreator.generatorSettings("{\"structures\":{\"structures\":{}},\"layers\":[{\"block\": \"air\", \"height\":1}], \"biome\":\"plains\"}");
        worldCreator.createWorld();
    }

    /**
     * Creates Multiverse world
     */
    protected void createMultiverseWorld() {
        // Check if world creator is configured and add new world to multiverse world manager
        if (worldCreator != null) {
            worldManager.addWorld(plot.getWorld().getWorldName(), worldCreator.environment(), null, worldCreator.type(),false, "VoidGen:{\"caves\":false,\"decoration\":false,\"mobs\":false,\"structures\":false,\"noise\":false,\"surface\":false,\"bedrock\":false}");
        } else {
            throw new RuntimeException("World Creator is not configured");
        }
    }

    /**
     * Generates plot schematic and outlines
     * @param plotSchematic - schematic file
     */
    protected void generateOutlines(File plotSchematic) {
        try {
            if (plotSchematic != null) {
                BlockVector3 buildingOutlinesCoordinates = PlotManager.getPlotCenter();

                com.sk89q.worldedit.world.World weWorld = new BukkitWorld(plot.getWorld().getBukkitWorld());
                Clipboard clipboard = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(new FileInputStream(plotSchematic)).read();

                // Place the bottom part of the schematic 5 blocks above 0
                int heightDif = clipboard.getOrigin().getY() - clipboard.getMinimumPoint().getY();
                buildingOutlinesCoordinates = buildingOutlinesCoordinates.add(0, heightDif, 0);

                ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard);
                EditSession editSession = PlotSystem.DependencyManager.getWorldEdit().newEditSession(weWorld);

                Operation operation = clipboardHolder.createPaste(editSession).to(buildingOutlinesCoordinates).ignoreAirBlocks(false).build();
                Operations.complete(operation);
                editSession.close();

                plot.getWorld().getBukkitWorld().setSpawnLocation(plot.getWorld().getSpawnPoint());
            }
        } catch (IOException | WorldEditException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "An error occurred while generating plot outlines!", ex);
            throw new RuntimeException("Plot outlines generation completed exceptionally");
        }
    }

    /**
     * Configures plot world
     * @param mvWorld - plot world
     */
    protected void configureWorld(@NotNull MultiverseWorld mvWorld) {
        // Set Bukkit world game rules
        plot.getWorld().getBukkitWorld().setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        plot.getWorld().getBukkitWorld().setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        plot.getWorld().getBukkitWorld().setGameRule(GameRule.DO_FIRE_TICK, false);
        plot.getWorld().getBukkitWorld().setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        plot.getWorld().getBukkitWorld().setGameRule(GameRule.KEEP_INVENTORY, true);
        plot.getWorld().getBukkitWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);

        // Set world time to midday
        plot.getWorld().getBukkitWorld().setTime(6000);

        // Configure multiverse world
        mvWorld.setAllowFlight(true);
        mvWorld.setGameMode(GameMode.CREATIVE);
        mvWorld.setEnableWeather(false);
        mvWorld.setDifficulty(Difficulty.PEACEFUL);
        mvWorld.setAllowAnimalSpawn(false);
        mvWorld.setAllowMonsterSpawn(false);
        mvWorld.setAutoLoad(false);
        mvWorld.setKeepSpawnInMemory(false);
        worldManager.saveWorldsConfig();
    }

    /**
     * Creates plot protection
     */
    protected void createProtection() {
        BlockVector3 min = BlockVector3.at(0, 1, 0);
        BlockVector3 max = BlockVector3.at(PlotManager.PLOT_SIZE, 256, PlotManager.PLOT_SIZE);

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(plot.getWorld().getBukkitWorld()));

        // Create protected region for world
        GlobalProtectedRegion globalRegion = new GlobalProtectedRegion("__global__");
        globalRegion.setFlag(Flags.ENTRY, StateFlag.State.DENY);
        globalRegion.setFlag(Flags.ENTRY.getRegionGroupFlag(), RegionGroup.ALL);

        // Create protected region for plot
        ProtectedRegion protectedPlotRegion = new ProtectedCuboidRegion(plot.getWorld().getWorldName(), min, max);
        protectedPlotRegion.setPriority(100);

        // Add and save regions
        try {
            if (regionManager != null) {
                regionManager.addRegion(globalRegion);
                regionManager.addRegion(protectedPlotRegion);
                regionManager.saveChanges();
            } else {
                throw new RuntimeException("Region Manager is null");
            }
        } catch (StorageException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "An error occurred while saving plot protection!", ex);
            throw new RuntimeException("Plot protection creation completed exceptionally");
        }


        // Add plot owner
        DefaultDomain owner = protectedPlotRegion.getOwners();
        owner.addPlayer(builder.getUUID());
        protectedPlotRegion.setOwners(owner);


        // Set permissions
        protectedPlotRegion.setFlag(Flags.PASSTHROUGH, StateFlag.State.ALLOW);
        protectedPlotRegion.setFlag(Flags.PASSTHROUGH.getRegionGroupFlag(), RegionGroup.OWNERS);

        protectedPlotRegion.setFlag(Flags.ENTRY, StateFlag.State.ALLOW);
        protectedPlotRegion.setFlag(Flags.ENTRY.getRegionGroupFlag(), RegionGroup.ALL);

        FileConfiguration config = PlotSystem.getPlugin().getConfigManager().getCommandsConfig();

        List<String> allowedCommandsNonBuilder = config.getStringList(ConfigPaths.ALLOWED_COMMANDS_NON_BUILDERS);
        allowedCommandsNonBuilder.removeIf(c -> c.equals("/cmd1"));
        for (BaseCommand baseCommand : PlotSystem.getPlugin().getCommandManager().getBaseCommands()) {
            allowedCommandsNonBuilder.addAll(Arrays.asList(baseCommand.getNames()));
            for (String command : baseCommand.getNames()) {
                allowedCommandsNonBuilder.add("/" + command);
            }
        }

        List<String> blockedCommandsBuilders = config.getStringList(ConfigPaths.BLOCKED_COMMANDS_BUILDERS);
        blockedCommandsBuilders.removeIf(c -> c.equals("/cmd1"));

        protectedPlotRegion.setFlag(Flags.BLOCKED_CMDS, new HashSet<>(blockedCommandsBuilders));
        protectedPlotRegion.setFlag(Flags.BLOCKED_CMDS.getRegionGroupFlag(), RegionGroup.OWNERS);

        protectedPlotRegion.setFlag(Flags.ALLOWED_CMDS, new HashSet<>(allowedCommandsNonBuilder));
        protectedPlotRegion.setFlag(Flags.ALLOWED_CMDS.getRegionGroupFlag(), RegionGroup.NON_OWNERS);
    }

    /**
     * Gets invoked when generation is completed
     * @param failed - true if generation has failed
     * @throws SQLException - caused by a database exception
     */
    protected void onComplete(boolean failed) throws SQLException {
        if (!failed) {
            builder.setPlot(plot.getID(), builder.getFreeSlot());
            plot.setStatus(Status.unfinished);
            plot.setPlotOwner(builder.getPlayer().getUniqueId().toString());
        }
    }

    /**
     * Gets invoked when an exception has occurred
     * @param ex - caused exception
     */
    protected void onException(Throwable ex) {
        Bukkit.getLogger().log(Level.SEVERE, "An error occurred while generating plot!", ex);
        builder.getPlayer().sendMessage(Utils.getErrorMessageFormat("An error occurred while generating plot! Please try again!"));
        builder.getPlayer().playSound(builder.getPlayer().getLocation(), Utils.ErrorSound,1,1);
    }

    /**
     * @return - plot object
     */
    public Plot getPlot() {
        return plot;
    }

    /**
     * @return - builder object
     */
    public Builder getBuilder() {
        return builder;
    }
}
