package com.alpsbte.plotsystem.commands.admin.dev;

import com.alpsbte.plotsystem.commands.BaseCommand;
import com.alpsbte.plotsystem.core.system.Builder;
import com.alpsbte.plotsystem.core.system.plot.Plot;
import com.alpsbte.plotsystem.core.system.plot.PlotManager;
import com.alpsbte.plotsystem.utils.Utils;
import com.alpsbte.plotsystem.utils.enums.Status;
import com.sk89q.worldedit.WorldEditException;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;

public class CMD_PlotComplete extends BaseCommand {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] args) {
        if (sender.hasPermission(getPermission())) {
            try {
                Plot plot;
                if (args.length > 0 && Utils.TryParseInt(args[0]) != null) {
                    int plotID = Integer.parseInt(args[0]);
                    if (PlotManager.plotExists(plotID)) {
                        plot = new Plot(plotID);
                        try {
                            if (!PlotManager.savePlotAsSchematic(plot)) {
                                sender.sendMessage(Utils.getErrorMessageFormat("Could not save plot. Please try again!"));
                                Bukkit.getLogger().log(Level.WARNING, "Could not save finished plot schematic (ID: " + plot.getID() + ")!");
                            }
                        } catch (IOException | SQLException | WorldEditException ex) {
                            Bukkit.getLogger().log(Level.WARNING, "Could not save finished plot schematic (ID: " + plot.getID() + ")!", ex);
                        }
                        plot.setStatus(Status.completed);
                        plot.getReview().setFeedbackSent(false);
                        plot.getReview().setFeedback("No Feedback");
                        plot.getPlotOwner().addCompletedBuild(1);

                        // Remove Plot from Owner
                        plot.getPlotOwner().removePlot(plot.getSlot());

                        if (!plot.getPlotMembers().isEmpty()) {
                            for (Builder builder : plot.getPlotMembers()) {
                                // Remove Slot from Member
                                builder.removePlot(builder.getSlot(plot));
                            }
                        }
                    } else {
                        sender.sendMessage(Utils.getErrorMessageFormat("This plot does not exist!"));

                    }
                } else if (getPlayer(sender) != null && PlotManager.isPlotWorld(getPlayer(sender).getWorld())) {
                    plot = PlotManager.getPlotByWorld(getPlayer(sender).getWorld());

                    try {
                        if (!PlotManager.savePlotAsSchematic(plot)) {
                            sender.sendMessage(Utils.getErrorMessageFormat("Could not save plot. Please try again!"));
                            Bukkit.getLogger().log(Level.WARNING, "Could not save finished plot schematic (ID: " + plot.getID() + ")!");

                        }
                    } catch (IOException | SQLException | WorldEditException ex) {
                        Bukkit.getLogger().log(Level.WARNING, "Could not save finished plot schematic (ID: " + plot.getID() + ")!", ex);

                    }
                    plot.setStatus(Status.completed);
                    plot.getReview().setFeedbackSent(false);
                    plot.getReview().setFeedback("No Feedback");
                    plot.getPlotOwner().addCompletedBuild(1);

                    // Remove Plot from Owner
                    plot.getPlotOwner().removePlot(plot.getSlot());

                    if (!plot.getPlotMembers().isEmpty()) {
                        for (Builder builder : plot.getPlotMembers()) {
                            // Remove Slot from Member
                            builder.removePlot(builder.getSlot(plot));
                        }
                    }
                } else {
                    sender.sendMessage(Utils.getErrorMessageFormat("This plot does not exist!"));

                }
            } catch (Exception e) {
                e.printStackTrace();

            }
        } else {
            sendInfo(sender);
        }
        return true;
    }

    @Override
    public String[] getNames() {return new String[]{"completeplot"};}

    @Override
    public String getDescription() {
        return "Forces the plot to be completed as if it's been accepted to the Terra server.\n" +
                "Requires Dev-Mode to be enabled in config.";
    }

    @Override
    public String[] getParameter() {
        return new String[] { "ID" };
    }

    @Override
    public String getPermission() {
        return "plotsystem.plot.complete";
    }

}
