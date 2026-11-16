package com.github.dev34.videoengine.resourcepack;

import com.github.dev34.videoengine.VideoEnginePlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.util.Enumeration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.bukkit.Bukkit.getServer;

public class PackCreator {

    private static final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    public static File getResourcePacksFolder(){
        return createFolder(VideoEnginePlugin.plugin.getDataFolder(), "resource_packs");
    }

    public static File getGlobalFolder(){
        return createFolder(getResourcePacksFolder(), "global");
    }

    public static File createFolder(File location, String name){
        File folder = new File(location, name);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public static boolean isValidName(String name) {
        return name != null && name.matches("[a-z0-9_]{1,64}");
    }

    public static void validateUrl(String videoUrl) throws Exception {
        URI uri = new URI(videoUrl);
        String scheme = uri.getScheme();

        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only http and https URLs are allowed.");
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Invalid URL.");
        }

        for (InetAddress address : InetAddress.getAllByName(host)) {
            byte[] bytes = address.getAddress();
            boolean uniqueLocal = bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || uniqueLocal) {
                throw new IllegalArgumentException("That URL points to a private address.");
            }
        }
    }

    public static File getGlobalResourcePack(String name) {
        File folder = createFolder(getResourcePacksFolder(), "global");
        return new File(folder, name);
    }

    public static File[] getGlobalResourcePacks(){
        File folder = createFolder(getResourcePacksFolder(), "global");
        return folder.listFiles();
    }

    public static File getResourcePack(UUID playerUUID, String name) {
        File folder = createFolder(getResourcePacksFolder(), playerUUID.toString());
        return new File(folder, name);
    }

    public static File[] getResourcePacks(UUID playerUUID){
        File folder = createFolder(getResourcePacksFolder(), playerUUID.toString());
        return folder.listFiles();
    }

    public static int getFrameCount(UUID playerUUID, String name) {
        File root = new File(VideoEnginePlugin.plugin.getDataFolder(), "resource_packs");
        File[] candidates = {
                new File(new File(root, playerUUID.toString()), name + ".zip"),
                new File(new File(root, "global"), name + ".zip")
        };

        for (File candidate : candidates) {
            if (!candidate.isFile()) continue;
            int count = countFrames(candidate, name);
            if (count > 0) return count;
        }

        return -1;
    }

    private static int countFrames(File zip, String name) {
        String prefix = "assets/minecraft/textures/font/" + name + "/frame_";
        int count = 0;

        try (ZipFile zipFile = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                if (entries.nextElement().getName().startsWith(prefix)) {
                    count++;
                }
            }
        } catch (Exception e) {
            return -1;
        }

        return count;
    }

    public static void createPack(String name, String videoUrl, boolean global, int packFormat, Player creator) {

        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid name. Use only lowercase letters, numbers and underscores.");
        }

        String path = global ? "global" : creator.getUniqueId().toString();
        File zip = new File(createFolder(getResourcePacksFolder(), path), name + ".zip");
        String lockKey = zip.getAbsolutePath();

        if (!inProgress.add(lockKey)) {
            throw new IllegalStateException("That pack is already being created.");
        }

        String type = global ? "global" : "local";

        getServer().getScheduler().runTaskAsynchronously(VideoEnginePlugin.plugin, () -> {

            try {
                validateUrl(videoUrl);

                int frames = FrameExtractor.extractFrames(videoUrl, zip, packFormat);
                int seconds = Math.max(1, frames / FrameExtractor.FPS);

                getServer().getScheduler().runTask(VideoEnginePlugin.plugin, () -> {
                    if (creator.isOnline()) creator.sendMessage(ChatColor.GREEN
                            + "Resource pack created (" + seconds + " seconds long)! Download it with /videoengine download "
                            + type + " " + name);
                });

            } catch (IllegalArgumentException e) {
                getServer().getScheduler().runTask(VideoEnginePlugin.plugin, () -> {
                    if (creator.isOnline()) creator.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
                });

            } catch (Exception e) {
                VideoEnginePlugin.plugin.getLogger().log(Level.WARNING, "Failed to create resource pack", e);
                getServer().getScheduler().runTask(VideoEnginePlugin.plugin, () -> {
                    if (creator.isOnline()) creator.sendMessage(ChatColor.RED + "Error: Failed to create resource pack. Are you sure you uploaded a video file? (Error 2)");
                });

            } finally {
                inProgress.remove(lockKey);
            }

        });
    }

}