package com.walshydev.autocleanbot;

import com.walshydev.autocleanbot.commands.AutoCleanCommand;
import com.walshydev.autocleanbot.commands.CleanCommand;
import com.walshydev.jba.Config;
import com.walshydev.jba.JBA;
import com.walshydev.jba.SQLController;
import com.walshydev.jba.scheduler.JBATask;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageHistory;
import net.dv8tion.jda.core.entities.TextChannel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AutoCleanBot extends JBA {

    private static AutoCleanBot instance;

    public static void main(String[] args) {
        (instance = new AutoCleanBot()).init();
    }

    private void init() {
        Config config = new Config("config");

        setupMySQL(config.getString("mysql.username"), config.getString("mysql.password"), config.getString("mysql.host"),
                config.getString("mysql.database"));

        init(AccountType.BOT, config.getString("bot.token"), config.getString("bot.prefix"));
    }

    public void run() {
        registerCommand(new CleanCommand());
        registerCommand(new AutoCleanCommand());

        scheduleCleans();
    }

    private void scheduleCleans() {
        try {
            SQLController.runSqlTask(connection -> {
               connection.createStatement().execute("CREATE TABLE IF NOT EXISTS autoclean_tasks (guild_id VARCHAR(20) NOT NULL, " +
                       "channel_id VARCHAR(20) NOT NULL, clear_pins TINYINT(1) DEFAULT 1, clean_schedule BIGINT(15), PRIMARY KEY(guild_id, channel_id))");

               ResultSet set = connection.createStatement().executeQuery("SELECT * FROM autoclean_tasks");
               while(set.next()) {
                   new JBATask(set.getString("channel_id")) {
                       @Override
                       public void run() {
                           try {
                               if (getClient().getGuildById(set.getString("guild_id")) == null ||
                                       getClient().getGuildById(set.getString("guild_id")).getTextChannelById(set.getString("channel_id")) == null) {
                                   set.deleteRow();
                                   return;
                               }
                               cleanChannel(getClient().getGuildById(set.getString("guild_id")).getTextChannelById(set.getString("channel_id")),
                                       set.getBoolean("clear_pins"));
                           } catch (SQLException e) {
                               e.printStackTrace();
                           }
                       }
                   }.repeat(set.getLong("clean_schedule"), set.getLong("clean_schedule"));
               }
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void cleanChannel(TextChannel channel, boolean deletePins) {
        MessageHistory history = channel.getHistory();
        while (true) {
            if (history.retrievePast(100).complete().isEmpty())
                break;
        }
        int i = 0;
        List<Message> toDelete = new ArrayList<>();
        for (Message m : history.getRetrievedHistory()) {
            if(m.isPinned() && !deletePins) continue;
            if (m.getCreationTime().plusWeeks(2).isAfter(OffsetDateTime.now())) {
                i++;
                toDelete.add(m);
            }else
                break;
            if (toDelete.size() == 100) {
                channel.deleteMessages(toDelete).complete();
                toDelete.clear();
            }
        }
        if (!toDelete.isEmpty()) {
            if (toDelete.size() != 1)
                channel.deleteMessages(toDelete).complete();
            else toDelete.forEach(msg -> msg.delete().complete());
        }
        channel.sendMessage(new EmbedBuilder()
                .setDescription(String.format("Deleted `%s` messages!", i)).build())
                .queue(s -> new JBATask("Delete Message " + s) {
                    @Override
                    public void run() {
                        s.delete().queue();
                    }
                }.delay(TimeUnit.SECONDS.toMillis(5)));
    }

    public static AutoCleanBot getInstance() {
        return instance;
    }
}
