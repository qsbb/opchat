package com.opchat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class WhisperHistory {
    private static final int MAX_PER_CONTACT = 100;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Gson GSON = new Gson();

    private static final Map<String, List<WhisperEntry>> history = new HashMap<>();
    private static final List<String> recentContacts = new ArrayList<>();
    private static final Set<String> hiddenContacts = new LinkedHashSet<>();
    private static final List<String> pinnedContacts = new ArrayList<>();
    private static final Map<String, String> nicknames = new HashMap<>();
    private static boolean loaded = false;
    private static boolean metaLoaded = false;

    public record WhisperEntry(boolean outgoing, String content, String time, boolean system, Long timestamp) {
        public WhisperEntry(boolean outgoing, String content, String time) {
            this(outgoing, content, time, false, System.currentTimeMillis());
        }
        public WhisperEntry(boolean outgoing, String content, String time, boolean system) {
            this(outgoing, content, time, system, System.currentTimeMillis());
        }
    }

    private static Path getFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("opchat/whispers.json");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Path f = getFile();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Map<String, List<WhisperEntry>> data = GSON.fromJson(r,
                new TypeToken<Map<String, List<WhisperEntry>>>(){}.getType());
            if (data != null) history.putAll(data);
        } catch (Exception ignored) {}
        cleanupExpired();
        recentContacts.clear();
        recentContacts.addAll(history.keySet());
        recentContacts.sort((a, b) -> {
            List<WhisperEntry> la = history.get(a), lb = history.get(b);
            if (la == null || la.isEmpty()) return 1;
            if (lb == null || lb.isEmpty()) return -1;
            return lb.get(lb.size() - 1).time().compareTo(la.get(la.size() - 1).time());
        });
    }

    private static void cleanupExpired() {
        int maxDays = ChatBubbleConfig.WHISPER_HISTORY_DAYS;
        if (maxDays <= 0) return;
        long cutoff = System.currentTimeMillis() - (long) maxDays * 24 * 60 * 60 * 1000L;
        boolean changed = false;
        for (var entry : history.entrySet()) {
            var list = entry.getValue();
            if (list.removeIf(e -> e.timestamp() != null && e.timestamp() < cutoff)) {
                changed = true;
            }
        }
        if (history.entrySet().removeIf(e -> e.getValue().isEmpty())) {
            changed = true;
        }
        if (changed) save();
    }

    private static void save() {
        Path f = getFile();
        try {
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(history, w);
            }
        } catch (Exception ignored) {}
    }

    public static void addOutgoing(String contact, String content) {
        if (contact == null || contact.isEmpty() || content == null || content.isEmpty()) return;
        load();
        List<WhisperEntry> list = history.computeIfAbsent(contact, k -> new ArrayList<>());
        if (!list.isEmpty()) {
            WhisperEntry last = list.get(list.size() - 1);
            if (last.outgoing() && last.content().equals(content)) return;
        }
        list.add(new WhisperEntry(true, content, LocalTime.now().format(TIME_FMT)));
        trim(list);
        touchContact(contact);
        save();
    }

    public static void addIncoming(String contact, String content) {
        if (contact == null || contact.isEmpty() || content == null || content.isEmpty()) return;
        load();
        List<WhisperEntry> list = history.computeIfAbsent(contact, k -> new ArrayList<>());
        if (!list.isEmpty()) {
            WhisperEntry last = list.get(list.size() - 1);
            if (!last.outgoing() && last.content().equals(content)) return;
        }
        list.add(new WhisperEntry(false, content, LocalTime.now().format(TIME_FMT)));
        trim(list);
        touchContact(contact);
        save();
    }

    private static void touchContact(String contact) {
        recentContacts.remove(contact);
        recentContacts.add(0, contact);
    }

    public static void addSystemMessage(String contact, String content) {
        if (contact == null || contact.isEmpty() || content == null || content.isEmpty()) return;
        load();
        List<WhisperEntry> list = history.computeIfAbsent(contact, k -> new ArrayList<>());
        list.add(new WhisperEntry(false, content, LocalTime.now().format(TIME_FMT), true));
        trim(list);
        save();
    }

    public static List<String> getRecentContacts() {
        load();
        return new ArrayList<>(recentContacts);
    }

    // === Hidden / Pinned contacts ===

    private static Path getMetaFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("opchat/contacts_meta.json");
    }

    private static void loadMeta() {
        if (metaLoaded) return;
        metaLoaded = true;
        Path f = getMetaFile();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Map<String, Object> obj = GSON.fromJson(r,
                com.google.gson.reflect.TypeToken.getParameterized(Map.class, String.class, Object.class).getType());
            if (obj != null) {
                Object hidden = obj.get("hidden");
                if (hidden instanceof List<?> hl) {
                    hiddenContacts.clear();
                    for (var h : hl) hiddenContacts.add(String.valueOf(h));
                }
                Object pinned = obj.get("pinned");
                if (pinned instanceof List<?> pl) {
                    pinnedContacts.clear();
                    for (var p : pl) pinnedContacts.add(String.valueOf(p));
                }
                Object nicks = obj.get("nicknames");
                if (nicks instanceof Map<?, ?> nm) {
                    nicknames.clear();
                    for (var e : nm.entrySet()) {
                        nicknames.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void saveMeta() {
        Path f = getMetaFile();
        try {
            Files.createDirectories(f.getParent());
            Map<String, Object> data = new HashMap<>();
            data.put("hidden", new ArrayList<>(hiddenContacts));
            data.put("pinned", new ArrayList<>(pinnedContacts));
            data.put("nicknames", new HashMap<>(nicknames));
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception ignored) {}
    }

    public static boolean isHidden(String contact) {
        loadMeta();
        return hiddenContacts.contains(contact);
    }

    public static List<String> getHiddenContacts() {
        loadMeta();
        return new ArrayList<>(hiddenContacts);
    }

    public static String getNickname(String contact) {
        loadMeta();
        return nicknames.get(contact);
    }

    public static void setNickname(String contact, String nickname) {
        loadMeta();
        if (nickname == null || nickname.isEmpty()) nicknames.remove(contact);
        else nicknames.put(contact, nickname);
        saveMeta();
    }

    public static String getDisplayName(String contact) {
        loadMeta();
        String nick = nicknames.get(contact);
        return (nick != null && !nick.isEmpty()) ? nick : contact;
    }

    public static void setHidden(String contact, boolean hidden) {
        loadMeta();
        if (hidden) hiddenContacts.add(contact);
        else hiddenContacts.remove(contact);
        saveMeta();
    }

    public static boolean isPinned(String contact) {
        loadMeta();
        return pinnedContacts.contains(contact);
    }

    public static void togglePin(String contact) {
        loadMeta();
        if (pinnedContacts.contains(contact)) pinnedContacts.remove(contact);
        else pinnedContacts.add(contact);
        saveMeta();
    }

    public static List<String> getPinnedContacts() {
        loadMeta();
        return new ArrayList<>(pinnedContacts);
    }

    private static void trim(List<WhisperEntry> list) {
        while (list.size() > MAX_PER_CONTACT) list.remove(0);
    }

    public static List<WhisperEntry> get(String contact) {
        load();
        return history.getOrDefault(contact, Collections.emptyList());
    }

    public static List<ChatMessageStore.ChatMessage> getAsChatMessages(String contact) {
        load();
        List<WhisperEntry> entries = history.getOrDefault(contact, Collections.emptyList());
        List<ChatMessageStore.ChatMessage> result = new ArrayList<>();
        var player = MinecraftClient.getInstance().player;
        String selfName = player != null ? player.getName().getString() : "";
        UUID selfUuid = player != null ? player.getUuid() : new UUID(0, 0);

        for (var e : entries) {
            String senderName = e.outgoing() ? selfName : contact;
            UUID senderUuid = e.outgoing() ? selfUuid : ChatMessageStore.lookupPlayerUUID(contact);
            LocalTime time;
            try {
                time = LocalTime.parse(e.time(), TIME_FMT);
            } catch (Exception ex) {
                time = LocalTime.now();
            }
            result.add(new ChatMessageStore.ChatMessage(
                senderUuid,
                Text.literal(senderName),
                Text.literal(e.content()),
                time,
                e.outgoing(),
                e.system(),
                null,
                null,
                String.valueOf(e.content().hashCode()),
                1
            ));
        }
        return result;
    }
}
