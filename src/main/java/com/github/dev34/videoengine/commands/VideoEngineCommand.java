package com.github.dev34.videoengine.commands;

import com.github.dev34.videoengine.VideoEnginePlugin;
import com.github.dev34.videoengine.VideoPlayer;
import com.github.dev34.videoengine.resourcepack.PackCreator;
import com.github.dev34.videoengine.resourcepack.PackHost;
import com.github.dev34.videoengine.resourcepack.PackVersion;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

public class VideoEngineCommand implements CommandExecutor {

    private static final String helpMessage = VideoEnginePlugin.colorizeHex(
            "&7---------------&8[&b&l VideoEngine &8]&7---------------\n" +
                    "&#74c7e3/videoengine create <name> <url> [-global] &8-&f Create a resource pack\n" +
                    "&#74c7e3/videoengine download [player] <local|global> <name> &8-&f Send a resource pack to a player\n" +
                    "&&#74c7e3/videoengine play <name> [duration] [player] &8-&f Play a video (duration in seconds, defaults to the full video)\n" +
                    "&#74c7e3/videoengine stop [player] &8-&f Stop playing a video\n" +
                    "&#74c7e3/videoengine reload &8-&f Reload the configuration\n" +
                    "&#74c7e3/videoengine setup &8-&f See setup instructions\n" +
                    "&#74c7e3/videoengine help &8-&f See this list of commands\n");

    private static final String setupMessage = VideoEnginePlugin.colorizeHex(
            "&#74c7e3Setup Instructions:\n" +
                    "&f1. Create a pack with: &a/videoengine create <name> <video url> [-global]\n" +
                    "&f2. Download it with: &a/videoengine download <local|global> <name>\n" +
                    "&f3. Play it with: &a/videoengine play <name> [time in seconds]");

    private static final String playPerm = "videoengine.play";
    private static final String stopPerm = "videoengine.stop";
    private static final String playOthersPerm = "videoengine.play.others";
    private static final String stopOthersPerm = "videoengine.stop.others";
    private static final String adminPerm = "videoengine.admin";
    private static final String downloadOthersPerm = "videoengine.download.others";
    private static final String reloadPerm = "videoengine.reload";

    private static final String createUsage = "&cUsage: /videoengine create <name> <video url> [-global]";
    private static final String downloadUsage = "&cUsage: /videoengine download [player] <local|global> <name>";
    private static final String playUsage = "&cUsage: /videoengine play <name> [duration in seconds] [player]";
    private static final String stopUsage = "&cUsage: /videoengine stop [player]";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(helpMessage);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setup":
                setup(player);
                break;
            case "reload":
                reload(player);
                break;
            case "create":
                create(player, args);
                break;
            case "download":
                download(player, args);
                break;
            case "play":
                play(player, args);
                break;
            case "stop":
                stop(player, args);
                break;
            default:
                player.sendMessage(helpMessage);
        }

        return true;
    }

    private void send(Player player, String message) {
        player.sendMessage(VideoEnginePlugin.colorize(message));
    }

    private boolean requirePermission(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        send(player, "&cYou do not have permission to use this command! (" + permission + ")");
        return false;
    }

    private boolean isPackType(String value) {
        return value.equalsIgnoreCase("local") || value.equalsIgnoreCase("global");
    }

    private void setup(Player player) {
        if (!requirePermission(player, adminPerm)) return;
        player.sendMessage(setupMessage);
    }

    private void reload(Player player) {
        if (!requirePermission(player, reloadPerm)) return;
        VideoEnginePlugin.plugin.updateConfig();
        send(player, "&aConfiguration reloaded.");
    }

    private void create(Player player, String[] args) {
        if (!requirePermission(player, adminPerm)) return;

        if (args.length < 3 || args.length > 4) {
            send(player, createUsage);
            return;
        }

        boolean global = false;

        if (args.length == 4) {
            if (!args[3].equalsIgnoreCase("-global")) {
                send(player, createUsage);
                return;
            }
            global = true;
        }

        try {
            PackCreator.createPack(args[1], args[2], global, PackVersion.forServer(), player);
            send(player, "&aCreating resource pack, this may take a while...");
        } catch (Exception e) {
            send(player, "&cError: " + e.getMessage());
        }
    }

    private void download(Player player, String[] args) {
        if (args.length < 3) {
            send(player, downloadUsage);
            return;
        }

        Player target;
        int typeIndex;

        if (isPackType(args[1])) {
            target = player;
            typeIndex = 1;
        } else {
            target = Bukkit.getPlayerExact(args[1]);
            typeIndex = 2;

            if (target == null) {
                send(player, "&cPlayer not found.");
                return;
            }

            if (!player.equals(target) && !requirePermission(player, downloadOthersPerm)) {
                return;
            }
        }

        if (args.length != typeIndex + 2 || !isPackType(args[typeIndex])) {
            send(player, downloadUsage);
            return;
        }

        String type = args[typeIndex].toLowerCase();
        String packName = args[typeIndex + 1];

        if (packName.endsWith(".zip")) {
            packName = packName.substring(0, packName.length() - 4);
        }

        if (!PackCreator.isValidName(packName)) {
            send(player, "&cInvalid pack name.");
            return;
        }

        String fileName = packName + ".zip";
        File resourcePack = type.equals("global")
                ? PackCreator.getGlobalResourcePack(fileName)
                : PackCreator.getResourcePack(target.getUniqueId(), fileName);

        if (!resourcePack.isFile()) {
            send(player, "&cNo " + type + " pack named &f" + packName + "&c was found.");
            return;
        }

        PackHost host = VideoEnginePlugin.plugin.getHost();

        if (host == null) {
            send(player, "&cThe pack host is not running.");
            return;
        }

        host.applyToPlayer(target, resourcePack);
        send(player, "&aSending pack &f" + fileName + "&a to &f" + target.getName() + "&a.");
    }

    private void play(Player player, String[] args) {
        if (args.length < 2) {
            send(player, playUsage);
            return;
        }

        String video = args[1];
        Integer durationSeconds = null;
        int playerIndex = 2;

        if (args.length >= 3) {
            try {
                durationSeconds = Integer.parseInt(args[2]);
                playerIndex = 3;
            } catch (NumberFormatException ignored) {
            }
        }

        if (args.length > playerIndex + 1) {
            send(player, playUsage);
            return;
        }

        if (durationSeconds != null && durationSeconds <= 0) {
            send(player, "&cDuration must be greater than 0!");
            return;
        }

        if (!PackCreator.isValidName(video)) {
            send(player, "&cInvalid video name.");
            return;
        }

        Player target = args.length > playerIndex ? Bukkit.getPlayerExact(args[playerIndex]) : player;

        if (target == null) {
            send(player, "&cPlayer not found.");
            return;
        }

        boolean allowed;
        String required;

        if (player.equals(target)) {
            required = playPerm;
            allowed = player.hasPermission(playPerm) || player.hasPermission(playOthersPerm) || player.hasPermission(adminPerm);
        } else {
            required = playOthersPerm;
            allowed = player.hasPermission(playOthersPerm) || player.hasPermission(adminPerm);
        }

        if (!allowed) {
            send(player, "&cYou do not have permission to use this command! (" + required + ")");
            return;
        }

        if (VideoPlayer.getViewers().containsKey(target)) {
            send(player, "&c" + target.getName() + " is already watching a video.");
            return;
        }

        int totalFrames = PackCreator.getFrameCount(target.getUniqueId(), video);
        int frames;

        if (durationSeconds == null) {
            if (totalFrames <= 0) {
                send(player, "&cCould not find the length of that video. Create the pack first, or specify a duration in seconds.");
                return;
            }
            frames = totalFrames;
        } else {
            frames = (int) Math.min((long) durationSeconds * 20L, Integer.MAX_VALUE);
            if (totalFrames > 0 && frames > totalFrames) {
                frames = totalFrames;
            }
        }

        if (!VideoPlayer.displayFrames(target, video, frames)) {
            send(player, "&cThat duration is too long.");
            return;
        }

        int seconds = (frames + 19) / 20;
        send(player, "&aPlaying video &f'" + video + "'&a for &f" + target.getName() + "&a for &f" + seconds + " seconds");
    }

    private void stop(Player player, String[] args) {
        if (args.length > 2) {
            send(player, stopUsage);
            return;
        }

        Player target = args.length == 2 ? Bukkit.getPlayerExact(args[1]) : player;

        if (target == null) {
            send(player, "&cPlayer not found.");
            return;
        }

        boolean allowed;
        String required;

        if (player.equals(target)) {
            required = stopPerm;
            allowed = player.hasPermission(stopPerm) || player.hasPermission(stopOthersPerm) || player.hasPermission(adminPerm);
        } else {
            required = stopOthersPerm;
            allowed = player.hasPermission(stopOthersPerm) || player.hasPermission(adminPerm);
        }

        if (!allowed) {
            send(player, "&cYou do not have permission to use this command! (" + required + ")");
            return;
        }

        if (!VideoPlayer.getViewers().containsKey(target)) {
            send(player, "&c" + target.getName() + " is not watching a video.");
            return;
        }

        VideoPlayer.stopVideo(target);
        send(player, "&aVideo has been stopped for " + target.getName());
    }
}