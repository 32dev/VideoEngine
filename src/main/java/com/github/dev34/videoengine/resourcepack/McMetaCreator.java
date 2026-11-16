package com.github.dev34.videoengine.resourcepack;

import com.google.gson.JsonObject;

public final class McMetaCreator {

    private static final int SUPPORTED_FORMATS_SINCE = 18;
    private static final int MIN_FORMAT_SINCE = 65;
    private static final int MAX_FORMAT = 999;

    private McMetaCreator() {
    }

    public static JsonObject create(String description, int packFormat) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", description);

        if (packFormat >= MIN_FORMAT_SINCE) {
            pack.addProperty("min_format", packFormat);
            pack.addProperty("max_format", MAX_FORMAT);
        } else {
            pack.addProperty("pack_format", packFormat);

            if (packFormat >= SUPPORTED_FORMATS_SINCE) {
                JsonObject range = new JsonObject();
                range.addProperty("min_inclusive", packFormat);
                range.addProperty("max_inclusive", MAX_FORMAT);
                pack.add("supported_formats", range);
            }
        }

        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }
}