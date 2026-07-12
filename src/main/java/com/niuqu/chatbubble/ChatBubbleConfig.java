package com.niuqu.chatbubble;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChatBubbleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("e33chat-client.json");

    // Config values
    public static boolean ENABLED = true;
    public static boolean RED_DOT_ENABLED = true;
    public static boolean ANIMATION_ENABLED = true;
    public static boolean STRONG_HINT_ENABLED = true;
    public static boolean MENTION_STRONG_HINT_ENABLED = true;
    public static boolean SYSTEM_CHAT_AS_BUBBLE = false;
    public static boolean ANTI_SPAM = false;
    public static boolean CHAT_REPORT_COMPAT = false;
    public static boolean PREVIEW_ENABLED = true;
    public static int PREVIEW_LINES = 2;
    public static int PREVIEW_WIDTH = 150;
    public static String OWN_BUBBLE_COLOR = "#95EC69";
    public static String OTHER_BUBBLE_COLOR = "#4A4A4A";
    public static String OWN_TEXT_COLOR = "#0A0A0A";
    public static String OTHER_TEXT_COLOR = "#FFFFFF";

    private static ConfigData data = new ConfigData();

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                data = GSON.fromJson(r, ConfigData.class);
                if (data == null) data = new ConfigData();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        applyData();
        save();
    }

    public static void save() {
        updateData();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void applyData() {
        ENABLED = data.enabled;
        RED_DOT_ENABLED = data.red_dot;
        ANIMATION_ENABLED = data.animation;
        STRONG_HINT_ENABLED = data.strong_hint;
        MENTION_STRONG_HINT_ENABLED = data.mention_strong_hint;
        SYSTEM_CHAT_AS_BUBBLE = data.system_chat_as_bubble;
        ANTI_SPAM = data.anti_spam;
        CHAT_REPORT_COMPAT = data.chat_report_compat;
        PREVIEW_ENABLED = data.preview_enabled;
        PREVIEW_LINES = data.preview_lines;
        PREVIEW_WIDTH = data.preview_width;
        OWN_BUBBLE_COLOR = data.own_bubble_color;
        OTHER_BUBBLE_COLOR = data.other_bubble_color;
        OWN_TEXT_COLOR = data.own_text_color;
        OTHER_TEXT_COLOR = data.other_text_color;
    }

    private static void updateData() {
        data.enabled = ENABLED;
        data.red_dot = RED_DOT_ENABLED;
        data.animation = ANIMATION_ENABLED;
        data.strong_hint = STRONG_HINT_ENABLED;
        data.mention_strong_hint = MENTION_STRONG_HINT_ENABLED;
        data.system_chat_as_bubble = SYSTEM_CHAT_AS_BUBBLE;
        data.anti_spam = ANTI_SPAM;
        data.chat_report_compat = CHAT_REPORT_COMPAT;
        data.preview_enabled = PREVIEW_ENABLED;
        data.preview_lines = PREVIEW_LINES;
        data.preview_width = PREVIEW_WIDTH;
        data.own_bubble_color = OWN_BUBBLE_COLOR;
        data.other_bubble_color = OTHER_BUBBLE_COLOR;
        data.own_text_color = OWN_TEXT_COLOR;
        data.other_text_color = OTHER_TEXT_COLOR;
    }

    public static int parseHexColor(String hex, int defaultColor) {
        try {
            String h = hex.replace("#", "").trim();
            if (h.length() != 6) return defaultColor;
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return defaultColor;
        }
    }

    private static class ConfigData {
        boolean enabled = true;
        boolean red_dot = true;
        boolean animation = true;
        boolean strong_hint = true;
        boolean mention_strong_hint = true;
        boolean system_chat_as_bubble = false;
        boolean anti_spam = false;
        boolean chat_report_compat = false;
        boolean preview_enabled = true;
        int preview_lines = 2;
        int preview_width = 150;
        String own_bubble_color = "#95EC69";
        String other_bubble_color = "#4A4A4A";
        String own_text_color = "#0A0A0A";
        String other_text_color = "#FFFFFF";
    }
}
