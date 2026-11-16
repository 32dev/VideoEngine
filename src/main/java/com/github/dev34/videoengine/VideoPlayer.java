package com.github.dev34.videoengine;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;

public final class VideoPlayer {

    private static final HashMap<Player, VideoSession> viewers = new HashMap<>();

    public static HashMap<Player, VideoSession> getViewers() {
        return viewers;
    }

    public static void sendActionBar(Player player, String string){
        VideoEnginePlugin.plugin.audience
                .player(player)
                .sendActionBar(Component.text(string));
    }

    public static void sendActionBar(Player player, Component component){
        VideoEnginePlugin.plugin.audience
                .player(player)
                .sendActionBar(component);
    }

    public static void displayFrame(Player player, String font, int codePoint) {
        if (!Character.isValidCodePoint(codePoint)) return;

        String character;
        try {
            character = new String(Character.toChars(codePoint));
        } catch (IllegalArgumentException e) {
            return;
        }
        TextComponent component = Component.text(character).font(Key.key(font));
        sendActionBar(player, component);
    }

    public static boolean displayFrames(Player player, String font, int frames) {

        int[][] ranges = {
                {0xE000, 0xF8FF},
                {0xF0000, 0xFFFFD},
                {0x100000, 0x10FFFD}
        };

        int totalAvailable = 0;
        for (int[] range : ranges) {
            totalAvailable += range[1] - range[0] + 1;
        }

        if (frames <= 0 || frames > totalAvailable) return false;

        VideoSession session = new VideoSession(font, frames, ranges);
        viewers.put(player, session);

        int secondsTotal = frames / 20;
        int remainingSeconds = secondsTotal;
        int soundCount = (int) Math.ceil(secondsTotal / 10.0);

        String[] sounds = new String[soundCount];
        int[] durations = new int[soundCount];

        for (int i = 0; i < soundCount; i++) {
            int sec = Math.min(10, remainingSeconds);
            remainingSeconds -= sec;
            sounds[i] = "minecraft:" + font + "_" + i;
            durations[i] = sec * 20;
        }

        session.setSounds(sounds, durations);

        BukkitRunnable runnable = createTask(player, session);
        session.setTask(runnable);
        runnable.runTaskTimer(VideoEnginePlugin.plugin, 0L, 1L);
        return true;
    }

    private static BukkitRunnable createTask(Player player, VideoSession session) {
        return new BukkitRunnable() {

            int soundTickCounter = 0;

            @Override
            public void run() {

                if (!player.isOnline()) {
                    cancel();
                    viewers.remove(player);
                    return;
                }

                if (session.getCurrentFrame() >= session.getFrames()) {
                    cancel();
                    viewers.remove(player);
                    return;
                }

                if (session.getSounds() != null && session.getCurrentSound() < session.getSounds().length) {
                    if (soundTickCounter == 0) {
                        player.playSound(player, session.getSounds()[session.getCurrentSound()],
                                SoundCategory.MASTER, 1.0f, 1.0f);
                    }
                    soundTickCounter++;

                    if (soundTickCounter >= session.getSoundDurations()[session.getCurrentSound()]) {
                        soundTickCounter = 0;
                        session.setCurrentSound(session.getCurrentSound() + 1);
                    }
                }

                displayFrame(player, session.getFont(), session.getCodePoint());

                session.setCurrentFrame(session.getCurrentFrame() + 1);
                session.setCodePoint(session.getCodePoint() + 1);

                if (session.getRangeIndex() < session.getRanges().length &&
                        session.getCodePoint() > session.getRanges()[session.getRangeIndex()][1]) {
                    session.setRangeIndex(session.getRangeIndex() + 1);
                    if (session.getRangeIndex() < session.getRanges().length) {
                        session.setCodePoint(session.getRanges()[session.getRangeIndex()][0]);
                    }
                }
            }
        };
    }

    private static void seek(VideoSession session, int frame) {
        int[][] ranges = session.getRanges();
        int index = frame;
        int range = 0;

        while (range < ranges.length && index >= ranges[range][1] - ranges[range][0] + 1) {
            index -= ranges[range][1] - ranges[range][0] + 1;
            range++;
        }

        session.setCurrentFrame(frame);
        session.setRangeIndex(range);
        if (range < ranges.length) {
            session.setCodePoint(ranges[range][0] + index);
        }
    }

    public static void stopVideo(Player player) {
        VideoSession session = viewers.remove(player);
        if (session != null) {
            session.pause();
            if (player.isOnline()) {
                sendActionBar(player, Component.empty());
                player.stopAllSounds();
            }
        }
    }

    public static void pauseVideo(Player player) {
        VideoSession session = viewers.get(player);
        if (session != null) {
            session.pause();
            if (player.isOnline()) player.stopAllSounds();
        }
    }

    public static void resumeVideo(Player player) {
        VideoSession session = viewers.get(player);
        if (session == null) return;

        if (session.getSounds() != null && session.getCurrentSound() < session.getSoundDurations().length) {
            int start = 0;
            for (int i = 0; i < session.getCurrentSound(); i++) {
                start += session.getSoundDurations()[i];
            }
            seek(session, start);
        }

        BukkitRunnable runnable = createTask(player, session);
        session.setTask(runnable);
        runnable.runTaskTimer(VideoEnginePlugin.plugin, 0L, 1L);
    }
}