package com.walshydev.autocleanbot.commands;

import com.walshydev.autocleanbot.AutoCleanBot;
import com.walshydev.jba.JBA;
import com.walshydev.jba.SQLController;
import com.walshydev.jba.commands.Command;
import com.walshydev.jba.scheduler.JBATask;
import com.walshydev.jba.scheduler.Scheduler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import org.joda.time.Minutes;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.awt.Color;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AutoCleanCommand implements Command {

    private final PeriodFormatter formatter = new PeriodFormatterBuilder()
        .appendDays().appendSuffix("d")
        .appendHours().appendSuffix("h")
        .appendMinutes().appendSuffix("m")
        .appendSeconds().appendSuffix("s")
        .toFormatter();

    private PreparedStatement queryTasks;
    private PreparedStatement insertTask;
    private PreparedStatement delTask;

    @Override
    public void onCommand(User user, MessageChannel messageChannel, Message message, String[] args, Member member) {
        if (messageChannel.getType() != ChannelType.TEXT) return;
        if (!message.getGuild().getSelfMember()
            .hasPermission(Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY)) {
            messageChannel.sendMessage(new EmbedBuilder()
                .setColor(Color.red)
                .setDescription("I need the `Manage Message` and `Read Message History` permissions!")
                .build()).queue();
            return;
        }

        if (message.getGuild().getMember(user).hasPermission(Permission.MESSAGE_MANAGE)) {
            if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                StringBuilder sb = new StringBuilder();
                try {
                    SQLController.runSqlTask(connection -> {
                        if (queryTasks == null)
                            queryTasks = connection.prepareStatement("SELECT * FROM autoclean_tasks " +
                                "WHERE guild_id = ?");

                        queryTasks.setString(1, message.getGuild().getId());

                        ResultSet set = queryTasks.executeQuery();
                        while (set.next()) {
                            sb.append("<#")
                                .append(set.getString("channel_id"))
                                .append("> (")
                                .append(set.getString("channel_id"))
                                .append(") - ")
                                .append("`")
                                .append(set.getLong("clean_schedule"))
                                .append("ms` (Clear pins: ")
                                .append(set.getBoolean("clear_pins"))
                                .append(")\n");
                        }
                    });
                } catch (SQLException e) {
                    JBA.LOGGER.error("Failed to list tasks!", e);
                }
                messageChannel.sendMessage(new EmbedBuilder()
                    .setDescription("**AutoClean schuled tasks**\n" + sb.toString()).build())
                    .queue();
            } else if (args.length == 2 || args.length == 3) {
                if (args[0].equalsIgnoreCase("remove")) {
                    if (message.getMentionedChannels().isEmpty()) return;

                    TextChannel tc = message.getMentionedChannels().get(0);
                    if (tc.getGuild().getId().equals(message.getGuild().getId())) {
                        Scheduler.cancelTask(tc.getId());
                        try {
                            SQLController.runSqlTask(connection -> {
                                if (delTask == null)
                                    delTask = connection.prepareStatement(
                                        "DELETE FROM autoclean_tasks WHERE channel_id = ?");

                                delTask.setString(1, tc.getId());
                                delTask.execute();
                            });
                        } catch (SQLException e) {
                            JBA.LOGGER.error("Failed to delete task!", e);
                        }
                        messageChannel.sendMessage(new EmbedBuilder()
                            .setColor(Color.green)
                            .setDescription("Deleted autoclean task for <#" + tc.getId() + ">")
                            .build())
                            .queue();
                    } else {
                        messageChannel.sendMessage(new EmbedBuilder()
                            .setColor(Color.red)
                            .setDescription("You can't setup a task for another guild!")
                            .build())
                            .queue();
                    }
                    return;
                }

                if (message.getMentionedChannels().isEmpty()) return;
                TextChannel tc = message.getMentionedChannels().get(0);
                if (tc.getGuild().getId().equals(message.getGuild().getId())) {
                    Period p = formatter.parsePeriod(args[1]);

                    if (p.toStandardMinutes().isLessThan(Minutes.minutes(5))) {
                        messageChannel.sendMessage(new EmbedBuilder()
                            .setColor(Color.red)
                            .setDescription("Make sure the task is at least 5 minutes!")
                            .build())
                            .queue();
                        return;
                    }

                    boolean clearPins = (args.length == 3 && (
                        args[2].equalsIgnoreCase("y")
                            || args[2].equalsIgnoreCase("yes")
                            || args[2].equalsIgnoreCase("true")
                    ));
                    try {
                        SQLController.runSqlTask(connection -> {
                            if (insertTask == null)
                                insertTask = connection.prepareStatement(
                                    "INSERT INTO autoclean_tasks (" +
                                        "guild_id," +
                                        "channel_id," +
                                        "clear_pins," +
                                        "clean_schedule" +
                                        ") VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                                        "clear_pins = VALUES(clear_pins)," +
                                        "clean_schedule = VALUES(clean_schedule)");

                            insertTask.setString(1, message.getGuild().getId());
                            insertTask.setString(2, tc.getId());
                            insertTask.setBoolean(3, clearPins);
                            insertTask.setLong(4, (p.toStandardSeconds().getSeconds() * 1000));
                            insertTask.execute();

                            new JBATask(tc.getId()) {
                                @Override
                                public void run() {
                                    AutoCleanBot.getInstance().cleanChannel(tc, clearPins);
                                }
                            }.repeat(p.toStandardSeconds().getSeconds() * 1000,
                                p.toStandardSeconds().getSeconds() * 1000);
                        });
                    } catch (SQLException e) {
                        JBA.LOGGER.error("Failed to schedule task", e);
                        messageChannel.sendMessage(new EmbedBuilder()
                            .setColor(Color.red)
                            .setDescription("An error occurred while creating the task!")
                            .build())
                            .queue();
                        return;
                    }
                    messageChannel.sendMessage(new EmbedBuilder()
                        .setColor(Color.green)
                        .setDescription("Scheduled a task to clean <#" + tc.getId() + "> every " +
                            formatter.print(p) + "\n" +
                            "Clear pins: " + clearPins)
                        .build())
                        .queue();
                } else {
                    messageChannel.sendMessage(new EmbedBuilder()
                        .setColor(Color.red)
                        .setDescription("You can't setup a task for another guild!")
                        .build())
                        .queue();
                }
            } else {
                messageChannel.sendMessage(new EmbedBuilder()
                    .setColor(Color.red)
                    .setDescription("Usage: `autoclean (channel) (time) [delete pins, default = true]`" +
                        "\n\t`autoclean remove (channel)`" +
                        "\n\t`autoclean list`")
                    .build()).queue();
            }
        }
    }

    @Override
    public String getCommand() {
        return "autoclean";
    }

    @Override
    public String getDescription() {
        return "Setup an automatic clean for a channel.";
    }
}
