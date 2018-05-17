# AutoCleanBot
Automatically clean a channel in DIscord

# Prerequisites
* Bot application at https://discordapp.com/developers/applications/me
* Java
* MySQL

# Setup
* Make an application, turn it into a bot account and get the token, put that in the config.json
* Create a MySQL database for the bot (`CREATE DATABASE autocleanbot`). Put the database name and other details in the config.json
* Run the bot!

# Download
I have uploaded the bot and example config to GitHub and Dropbox for you to download.
GitHub: https://github.com/WalshyDev/AutoCleanBot/releases
Dropbox: https://www.dropbox.com/s/zwp10dbdfbcwqj0/AutoCleanBot.zip?dl=0

# Commands
!clean \[y\] - Cleans the channel you executed the command in, **wont clean pins unless y is passed**.  
!autoclean (#channel) (time) (clear pins, pass `y` here) - Sets up a task to clean the mentioned channel every specified time. Example usage: !autoclean #bot-tests 5m y - This will clean #bot-tests every 5 mins and clear the pins.  
!autoclean list - List the tasks.  
!autoclean remove (#channel) - Remove a task for a certain channel. Example usage: !autoclean remove #bot-tests
