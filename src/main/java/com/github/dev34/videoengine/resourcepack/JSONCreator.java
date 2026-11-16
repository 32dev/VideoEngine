package com.github.dev34.videoengine.resourcepack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class JSONCreator {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final int[][] RANGES = {
            {0xE000, 0xF8FF},
            {0xF0000, 0xFFFFD},
            {0x100000, 0x10FFFD}
    };

    public static final int MAX_FRAMES = totalAvailable();

    private JSONCreator() {
    }

    private static int totalAvailable() {
        int total = 0;
        for (int[] range : RANGES) {
            total += range[1] - range[0] + 1;
        }
        return total;
    }

    public static int codePoint(int index) {
        for (int[] range : RANGES) {
            int size = range[1] - range[0] + 1;
            if (index < size) {
                return range[0] + index;
            }
            index -= size;
        }
        throw new IllegalArgumentException("Frame index out of range");
    }

    public static JsonObject createFontJson(String fontName, int frames, int height, int ascent) {
        if (frames > MAX_FRAMES) {
            throw new IllegalArgumentException("Too many frames: " + frames);
        }

        JsonArray providers = new JsonArray();

        for (int i = 0; i < frames; i++) {
            JsonObject provider = new JsonObject();
            provider.addProperty("type", "bitmap");
            provider.addProperty("file", "minecraft:font/" + fontName + "/frame_" + i + ".png");
            provider.addProperty("ascent", ascent);
            provider.addProperty("height", height);

            JsonArray chars = new JsonArray();
            chars.add(new String(Character.toChars(codePoint(i))));
            provider.add("chars", chars);

            providers.add(provider);
        }

        JsonObject json = new JsonObject();
        json.add("providers", providers);
        return json;
    }

    public static JsonObject createSoundsJson(List<String> soundNames) {
        JsonObject json = new JsonObject();

        for (String name : soundNames) {
            JsonArray sounds = new JsonArray();
            sounds.add(name);

            JsonObject entry = new JsonObject();
            entry.add("sounds", sounds);
            json.add(name, entry);
        }

        return json;
    }

    public static byte[] toBytes(JsonElement json) {
        return GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
    }
}