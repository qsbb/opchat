package com.opchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChatBubbleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("opchat-client.json");

    // Config values
    public static boolean ENABLED = true;
    public static boolean RED_DOT_ENABLED = true;
    public static boolean ANIMATION_ENABLED = true;
    public static int PANEL_OPACITY = 30;
    public static boolean STRONG_HINT_ENABLED = true;
    public static boolean MENTION_STRONG_HINT_ENABLED = true;
    public static boolean SYSTEM_CHAT_AS_BUBBLE = false;
    public static boolean ANTI_SPAM = false;
    public static boolean CHAT_REPORT_COMPAT = false;
    public static int WHISPER_HISTORY_DAYS = 30;
    public static boolean PREVIEW_ENABLED = true;
    public static int PREVIEW_LINES = 2;
    public static int PREVIEW_WIDTH = 150;
    public static String OWN_BUBBLE_COLOR = "#95EC69";
    public static String OTHER_BUBBLE_COLOR = "#4A4A4A";
    public static String OWN_TEXT_COLOR = "#0A0A0A";
    public static String OTHER_TEXT_COLOR = "#FFFFFF";
    public static List<String> QUICK_INPUTS = new ArrayList<>();
    public static List<QuickCommand> QUICK_COMMANDS = new ArrayList<>();
    public static boolean MULTI_MODE_COMMANDS = false;
    public static Map<String, List<QuickCommand>> QUICK_COMMANDS_BY_MODE = new HashMap<>();
    public static List<ContactGroup> CONTACT_GROUPS = new ArrayList<>();

    public static class QuickCommand {
        public String display = "";
        public String command = "";

        public QuickCommand() {}

        public QuickCommand(String display, String command) {
            this.display = display != null ? display : "";
            this.command = command != null ? command : "";
        }

        public String getDisplay() {
            return (display == null || display.isEmpty()) ? command : display;
        }
    }

    public static class ContactGroup {
        public String name = "";
        public List<String> members = new ArrayList<>();
        public List<String> prefixes = new ArrayList<>();
        public transient boolean expanded = false;

        public ContactGroup() {}

        public ContactGroup(String name, List<String> members, List<String> prefixes) {
            this.name = name;
            this.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
            this.prefixes = prefixes != null ? new ArrayList<>(prefixes) : new ArrayList<>();
        }

        public boolean matches(String playerName) {
            if (playerName == null || playerName.isEmpty()) return false;
            if (members.contains(playerName)) return true;
            for (String prefix : prefixes) {
                if (prefix != null && !prefix.isEmpty() && playerName.startsWith(prefix)) return true;
            }
            return false;
        }
    }

    public static ContactGroup getGroupOf(String name) {
        if (name == null || name.isEmpty()) return null;
        for (var group : CONTACT_GROUPS) {
            if (group.matches(name)) return group;
        }
        return null;
    }

    public static boolean isGroupContact(String name) {
        return getGroupOf(name) != null;
    }

    public static void addToGroup(String groupName, String contactName) {
        if (groupName == null || groupName.isEmpty() || contactName == null || contactName.isEmpty()) return;
        for (var group : CONTACT_GROUPS) {
            if (group.name.equals(groupName)) {
                if (!group.members.contains(contactName)) {
                    group.members.add(contactName);
                }
                save();
                return;
            }
        }
    }

    public static void removeFromGroup(String groupName, String contactName) {
        if (groupName == null || groupName.isEmpty() || contactName == null || contactName.isEmpty()) return;
        for (var group : CONTACT_GROUPS) {
            if (group.name.equals(groupName)) {
                group.members.remove(contactName);
                save();
                return;
            }
        }
    }

    public static void addGroup(String name) {
        if (name == null || name.isEmpty()) return;
        for (var group : CONTACT_GROUPS) {
            if (group.name.equals(name)) return;
        }
        CONTACT_GROUPS.add(new ContactGroup(name, List.of(), List.of()));
        save();
    }

    public static void removeGroup(String name) {
        CONTACT_GROUPS.removeIf(g -> g.name.equals(name));
        save();
    }

    public static List<String> getGroupOnlineMembers(ContactGroup group, Set<String> onlineSet) {
        List<String> result = new ArrayList<>();
        if (group == null) return result;
        for (String name : group.members) {
            if (onlineSet.contains(name)) result.add(name);
        }
        for (String name : onlineSet) {
            if (group.members.contains(name)) continue;
            boolean prefixMatch = false;
            for (String prefix : group.prefixes) {
                if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix)) {
                    prefixMatch = true;
                    break;
                }
            }
            if (prefixMatch) result.add(name);
        }
        Collections.sort(result);
        return result;
    }

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
        PANEL_OPACITY = data.panel_opacity;
        STRONG_HINT_ENABLED = data.strong_hint;
        MENTION_STRONG_HINT_ENABLED = data.mention_strong_hint;
        SYSTEM_CHAT_AS_BUBBLE = data.system_chat_as_bubble;
        ANTI_SPAM = data.anti_spam;
        CHAT_REPORT_COMPAT = data.chat_report_compat;
        WHISPER_HISTORY_DAYS = data.whisper_history_days;
        PREVIEW_ENABLED = data.preview_enabled;
        PREVIEW_LINES = data.preview_lines;
        PREVIEW_WIDTH = data.preview_width;
        OWN_BUBBLE_COLOR = data.own_bubble_color;
        OTHER_BUBBLE_COLOR = data.other_bubble_color;
        OWN_TEXT_COLOR = data.own_text_color;
        OTHER_TEXT_COLOR = data.other_text_color;
        QUICK_INPUTS = data.quick_inputs != null ? new ArrayList<>(data.quick_inputs) : new ArrayList<>();
        QUICK_COMMANDS = data.quick_commands != null ? new ArrayList<>(data.quick_commands) : new ArrayList<>();
        MULTI_MODE_COMMANDS = data.multi_mode_commands;
        QUICK_COMMANDS_BY_MODE = data.quick_commands_by_mode != null ? new HashMap<>(data.quick_commands_by_mode) : new HashMap<>();
        CONTACT_GROUPS = data.contact_groups != null ? new ArrayList<>(data.contact_groups) : new ArrayList<>();
    }

    private static void updateData() {
        data.enabled = ENABLED;
        data.red_dot = RED_DOT_ENABLED;
        data.animation = ANIMATION_ENABLED;
        data.panel_opacity = PANEL_OPACITY;
        data.strong_hint = STRONG_HINT_ENABLED;
        data.mention_strong_hint = MENTION_STRONG_HINT_ENABLED;
        data.system_chat_as_bubble = SYSTEM_CHAT_AS_BUBBLE;
        data.anti_spam = ANTI_SPAM;
        data.chat_report_compat = CHAT_REPORT_COMPAT;
        data.whisper_history_days = WHISPER_HISTORY_DAYS;
        data.preview_enabled = PREVIEW_ENABLED;
        data.preview_lines = PREVIEW_LINES;
        data.preview_width = PREVIEW_WIDTH;
        data.own_bubble_color = OWN_BUBBLE_COLOR;
        data.other_bubble_color = OTHER_BUBBLE_COLOR;
        data.own_text_color = OWN_TEXT_COLOR;
        data.other_text_color = OTHER_TEXT_COLOR;
        data.quick_inputs = QUICK_INPUTS;
        data.quick_commands = QUICK_COMMANDS;
        data.multi_mode_commands = MULTI_MODE_COMMANDS;
        data.quick_commands_by_mode = QUICK_COMMANDS_BY_MODE;
        data.contact_groups = CONTACT_GROUPS;
    }

    public static List<QuickCommand> getActiveQuickCommands(String modeKey) {
        if (!MULTI_MODE_COMMANDS || modeKey == null) return QUICK_COMMANDS;
        List<QuickCommand> modeCmds = QUICK_COMMANDS_BY_MODE.get(modeKey);
        return modeCmds != null ? modeCmds : QUICK_COMMANDS;
    }

    public static List<QuickCommand> getOrCreateModeCommands(String modeKey) {
        if (modeKey == null) return QUICK_COMMANDS;
        return QUICK_COMMANDS_BY_MODE.computeIfAbsent(modeKey, k -> new ArrayList<>());
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
        int panel_opacity = 30;
        boolean strong_hint = true;
        boolean mention_strong_hint = true;
        boolean system_chat_as_bubble = false;
        boolean anti_spam = false;
        boolean chat_report_compat = false;
        int whisper_history_days = 30;
        boolean preview_enabled = true;
        int preview_lines = 2;
        int preview_width = 150;
        String own_bubble_color = "#95EC69";
        String other_bubble_color = "#4A4A4A";
        String own_text_color = "#0A0A0A";
        String other_text_color = "#FFFFFF";
        List<String> quick_inputs = new ArrayList<>();
        List<QuickCommand> quick_commands = new ArrayList<>();
        boolean multi_mode_commands = false;
        Map<String, List<QuickCommand>> quick_commands_by_mode = new HashMap<>();
        List<ContactGroup> contact_groups = new ArrayList<>();
    }
}
