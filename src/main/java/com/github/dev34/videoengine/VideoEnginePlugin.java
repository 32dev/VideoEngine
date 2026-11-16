package com.github.dev34.videoengine;

import com.github.dev34.videoengine.commands.VideoEngineCommand;
import com.github.dev34.videoengine.resourcepack.PackCreator;
import com.github.dev34.videoengine.resourcepack.PackHost;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VideoEnginePlugin extends JavaPlugin implements Listener {

    public static VideoEnginePlugin plugin;
    public static BukkitAudiences audience;

    public static String ipAddress;
    public static int port;
    public static boolean httpEnabled;

    private PackHost host;

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})|#([A-Fa-f0-9]{6})");
    private static final String COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    @Override
    public void onEnable() {

        plugin = this;
        audience = BukkitAudiences.create(this);
        updateConfig();

        Bukkit.getPluginCommand("videoengine").setExecutor(new VideoEngineCommand());
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(getName() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        for (Player player : new ArrayList<>(VideoPlayer.getViewers().keySet())) {
            VideoPlayer.stopVideo(player);
        }
        if (host != null) {
            host.stop();
        }
        if (audience != null) {
            audience.close();
        }
        getLogger().info(getName() + " has been disabled!");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        VideoPlayer.stopVideo(event.getPlayer());
    }

    public static boolean isPUA(String charCode){
        if (charCode != null && !charCode.isEmpty()){
            if (charCode.codePointCount(0, charCode.length()) == 1) {
                int codePoint = charCode.codePointAt(0);

                return (codePoint >= 0xE000 && codePoint <= 0xF8FF) ||
                        (codePoint >= 0xF0000 && codePoint <= 0xFFFFD) ||
                        (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
            }
            return false;
        }
        return false;
    }

    public static String colorize(String str){
        return ChatColor.translateAlternateColorCodes('&', str);
    }

    public static String colorizeHex(String message) {
        if (message == null) return null;

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        char[] chars = buffer.toString().toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && COLOR_CODES.indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }

        return new String(chars);
    }

    public PackHost getHost(){
        return host;
    }

    public PackHost updateHost(){
        if (this.host != null){
            this.host.stop();
            this.host = null;
        }

        if (!httpEnabled) {
            return null;
        }

        String address = ipAddress;
        if (address == null || address.isEmpty()) {
            address = Bukkit.getIp();
        }
        if (address == null || address.isEmpty()) {
            address = "127.0.0.1";
        }

        PackHost newHost = new PackHost(VideoEnginePlugin.plugin, PackCreator.getResourcePacksFolder(), address, port);
        try {
            newHost.start(port);
            this.host = newHost;
        } catch (Exception e) {
            newHost.stop();
            Bukkit.getLogger().severe("Could not start host: " + e.getMessage());
        }
        return this.host;
    }

    public void updateConfig(){
        saveDefaultConfig();
        reloadConfig();

        ipAddress = getConfig().getString("http-server.ip");
        port = getConfig().getInt("http-server.port");
        httpEnabled = getConfig().getBoolean("http-server.enabled");

        updateHost();

        getLogger().info("Config loaded!");
    }

}