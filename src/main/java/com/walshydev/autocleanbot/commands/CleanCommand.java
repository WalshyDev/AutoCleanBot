package com.walshydev.autocleanbot.commands;

import com.walshydev.autocleanbot.AutoCleanBot;
import com.walshydev.jba.commands.Command;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;

import java.awt.Color;

public class CleanCommand implements Command {

    public void onCommand(User user, MessageChannel messageChannel, Message message, String[] args, Member member) {
        if (messageChannel.getType() != ChannelType.TEXT) return;
        if (!message.getGuild().getSelfMember()
            .hasPermission(Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY)) {
            messageChannel.sendMessage(new EmbedBuilder()
                .setColor(Color.red)
                .setDescription("I need the `Manage Message` and `Read Message History` permissions!")
                .build())
                .queue();
            return;
        }

        if (message.getGuild().getMember(user).hasPermission(Permission.MESSAGE_MANAGE)) {
            AutoCleanBot.getInstance().cleanChannel((TextChannel) messageChannel,
                args.length == 1 && (args[0].equalsIgnoreCase("y")
                    || args[0].equalsIgnoreCase("yes")
                    || args[0].equalsIgnoreCase("true"))
            );
        }
    }

    public String getCommand() {
        return "clean";
    }

    public String getDescription() {
        return "Clean the entire channel. Ignores pins by default, add `y` to clean pins.";
    }
}
