package com.github.dev34.videoengine.resourcepack;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PackHost {

    private final JavaPlugin plugin;
    private final Path root;
    private final String baseUrl;
    private final Map<String, byte[]> hashCache = new ConcurrentHashMap<>();
    private final Map<String, Long> modifiedCache = new ConcurrentHashMap<>();
    private HttpServer server;
    private ExecutorService executor;

    public PackHost(JavaPlugin plugin, File packsFolder, String serverIP, int port) {
        this.plugin = plugin;
        this.root = packsFolder.getAbsoluteFile().toPath().normalize();
        this.baseUrl = "http://" + serverIP + ":" + port + "/";
    }

    private String key(File file) {
        return root.relativize(file.getAbsoluteFile().toPath().normalize()).toString().replace('\\', '/');
    }

    private boolean isInsideRoot(File file) {
        return file.getAbsoluteFile().toPath().normalize().startsWith(root);
    }

    private String encodePath(String key) {
        String[] segments = key.split("/");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) builder.append('/');
            builder.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return builder.toString();
    }

    public void start(int port) throws Exception {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.length() <= 1 || !path.endsWith(".zip")) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            Path target = root.resolve(path.substring(1)).normalize();
            File targetFile = target.toFile();

            if (!target.startsWith(root) || !targetFile.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, targetFile.length() == 0 ? -1 : targetFile.length());

            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(target, os);
            }
        });

        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    public void refreshHash(File resourcePack) throws Exception {
        String key = key(resourcePack);
        if (!resourcePack.exists()) {
            hashCache.remove(key);
            modifiedCache.remove(key);
            return;
        }
        long modified = resourcePack.lastModified();
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream is = Files.newInputStream(resourcePack.toPath());
             DigestInputStream dis = new DigestInputStream(is, digest)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
            }
        }
        hashCache.put(key, digest.digest());
        modifiedCache.put(key, modified);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public void applyToPlayer(Player player, File resourcePack) {
        if (resourcePack == null || !resourcePack.isFile() || !isInsideRoot(resourcePack)) {
            player.sendMessage(ChatColor.RED + "Resource pack not found!");
            return;
        }

        String key = key(resourcePack);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            byte[] sha1Hash;
            try {
                Long cachedModified = modifiedCache.get(key);
                if (cachedModified == null || cachedModified != resourcePack.lastModified()
                        || hashCache.get(key) == null) {
                    refreshHash(resourcePack);
                }
                sha1Hash = hashCache.get(key);
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ChatColor.RED + "Failed to hash resource pack!"));
                return;
            }

            if (sha1Hash == null) {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ChatColor.RED + "Failed to hash resource pack!"));
                return;
            }

            byte[] finalHash = sha1Hash;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                String packUrl = baseUrl + encodePath(key);
                UUID packId = UUID.nameUUIDFromBytes((packUrl + Arrays.toString(finalHash)).getBytes(StandardCharsets.UTF_8));

                player.setResourcePack(packId, packUrl, finalHash, (String) null, false);
            });
        });
    }
}