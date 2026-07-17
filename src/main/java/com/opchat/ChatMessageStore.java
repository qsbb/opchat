package com.opchat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class ChatMessageStore {
    private static final int MAX = 100;
    private static final List<ChatMessage> messages = new ArrayList<>();
    private static int unreadCount = 0;
    private static boolean screenOpen = false;
    private static String pendingReplyContent;
    private static String pendingReplySender;
    private static final List<PreviewEntry> previews = new ArrayList<>();
    private static final int PREVIEW_TICKS = 100;
    private static String strongHintText;
    private static int strongHintTicks;
    private static boolean strongHintIsMention;
    public static final int STRONG_HINT_DURATION = 60;

    private static String currentWorldKey;
    private static final Map<String, String> worldTitles = new HashMap<>();
    private static final Gson GSON = new Gson();
    private static boolean titlesLoaded;
    private static final Map<String, List<PersistedMessage>> savedHistories = new HashMap<>();
    private static boolean historiesLoaded;
    private static final Map<String, PendingMeta> pendingMetas = new HashMap<>();

    public record SenderMeta(UUID senderUUID, Text senderName,
                             Text rawContent, boolean isSystem) {}

    private static final List<SenderMeta> PENDING_META_QUEUE = new ArrayList<>();

    public static void setPendingMeta(SenderMeta meta) {
        PENDING_META_QUEUE.add(meta);
        if (PENDING_META_QUEUE.size() > 10) PENDING_META_QUEUE.remove(0);
    }

    public static SenderMeta consumePendingMeta(String messageContent) {
        for (int i = 0; i < PENDING_META_QUEUE.size(); i++) {
            SenderMeta meta = PENDING_META_QUEUE.get(i);
            String raw = meta.rawContent().getString();
            if (messageContent.contains(raw) || raw.contains(messageContent)) {
                PENDING_META_QUEUE.remove(i);
                return meta;
            }
        }
        return null;
    }

    private static String pendingWhisperTarget;
    private static String pendingWhisperSender;
    private static boolean pendingWhisperOutgoing;

    public static void setPendingWhisper(String target, String sender, boolean outgoing) {
        pendingWhisperTarget = target;
        pendingWhisperSender = sender;
        pendingWhisperOutgoing = outgoing;
    }

    public static boolean consumePendingWhisper(Text message) {
        if (pendingWhisperTarget == null) return false;
        String target = pendingWhisperTarget;
        String sender = pendingWhisperSender;
        boolean outgoing = pendingWhisperOutgoing;
        pendingWhisperTarget = null;
        pendingWhisperSender = null;
        pendingWhisperOutgoing = false;
        if (outgoing) {
            WhisperHistory.addOutgoing(target, message.getString());
        } else {
            WhisperHistory.addIncoming(sender, message.getString());
            if (!screenOpen) {
                ChatBubbleHudOverlay.showNotification(sender + ": " + message.getString());
            }
        }
        return true;
    }

    private static int pendingEchoCount;
    private static final List<String> pendingEchoTexts = new ArrayList<>();

    public static void incrementPendingEcho(String sentText) {
        pendingEchoCount++;
        pendingEchoTexts.add(sentText);
    }

    public static boolean consumeEchoBySystemChat(String incomingText) {
        if (pendingEchoCount <= 0) return false;
        for (int i = 0; i < pendingEchoTexts.size(); i++) {
            if (incomingText.contains(pendingEchoTexts.get(i))) {
                pendingEchoTexts.remove(i);
                pendingEchoCount--;
                return true;
            }
        }
        return false;
    }

    public static boolean consumeEchoIfSenderMatches(String senderName) {
        if (pendingEchoCount <= 0) return false;
        var player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        if (senderName.equals(player.getName().getString())) {
            pendingEchoCount--;
            return true;
        }
        return false;
    }

    public static UUID lookupPlayerUUID(String name) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return new UUID(0, 0);
        var connection = player.networkHandler;
        if (connection == null) return new UUID(0, 0);
        for (var info : connection.getPlayerList()) {
            if (info.getProfile().name().equals(name))
                return info.getProfile().id();
        }
        return new UUID(0, 0);
    }

    public static boolean isRecentDuplicate(String content) {
        int size = messages.size();
        for (int i = size - 1; i >= 0 && i >= size - 2; i--) {
            String existing = messages.get(i).content().getString();
            if (existing.contains(content) || content.contains(existing)) return true;
        }
        return false;
    }

    private record PendingMeta(UUID senderUUID, String quoteSender, String quoteContent, List<String> mentionTargets) {}

    public record ChatMessage(
        UUID senderUUID,
        Text senderName,
        Text content,
        LocalTime time,
        boolean isOwn,
        boolean isSystem,
        String replyContent,
        String replySender,
        String messageHash,
        int duplicateCount
    ) {}

    private record PersistedMessage(
        String senderUUID,
        String senderName,
        String content,
        String time,
        boolean isOwn,
        boolean isSystem,
        String replyContent,
        String replySender,
        String messageHash,
        int duplicateCount
    ) {}

    public static class PreviewEntry {
        public final String text;
        public int ticks;
        public PreviewEntry(String text, int ticks) {
            this.text = text;
            this.ticks = ticks;
        }
    }

    public static void addMessage(Text content, UUID senderUUID, Text senderName, boolean isSystem) {
        content = addUnderlineToClicks(content);
        String messageHash = String.valueOf(content.getString().hashCode());

        String playerName = MinecraftClient.getInstance().player != null
            ? MinecraftClient.getInstance().player.getName().getString() : "";
        boolean own = senderName != null && senderName.getString().equals(playerName);

        // 合并连续相同消息（仅他人消息，自己发的不合并）
        if (!own && !messages.isEmpty()) {
            ChatMessage last = messages.get(messages.size() - 1);
            if (!last.isSystem() && last.senderName().getString().equals(senderName.getString())
                && last.content().getString().equals(content.getString())) {
                messages.set(messages.size() - 1, new ChatMessage(
                    last.senderUUID(), last.senderName(), last.content(),
                    LocalTime.now(),
                    last.isOwn(), last.isSystem(),
                    last.replyContent(), last.replySender(), last.messageHash(),
                    last.duplicateCount() + 1
                ));
                saveCurrentHistory();
                return;
            }
        }

        PendingMeta pending = pendingMetas.remove(messageHash);

        String replyContent = null;
        String replySender = null;
        if (own && pendingReplyContent != null) {
            replyContent = pendingReplyContent;
            replySender = pendingReplySender;
            pendingReplyContent = null;
            pendingReplySender = null;
        } else if (pending != null && !pending.quoteContent().isEmpty()) {
            replyContent = pending.quoteContent();
            replySender = pending.quoteSender();
        }

        messages.add(new ChatMessage(
            senderUUID,
            senderName != null ? senderName : Text.literal(""),
            content,
            LocalTime.now(),
            own,
            isSystem,
            replyContent,
            replySender,
            messageHash,
            1
        ));

        while (messages.size() > MAX)
            messages.remove(0);
        saveCurrentHistory();

        if (!screenOpen) {
            unreadCount++;
            boolean systemToHint = isSystem && ChatBubbleConfig.STRONG_HINT_ENABLED;
            boolean mentionToHint = !own && !isSystem && ChatBubbleConfig.MENTION_STRONG_HINT_ENABLED
                && (content.getString().contains("@" + playerName)
                    || (replySender != null && replySender.equals(playerName)));

            if (mentionToHint) {
                strongHintText = Text.translatable("opchat.notif.mention").getString();
                strongHintTicks = STRONG_HINT_DURATION;
                strongHintIsMention = true;
            }

            if (ChatBubbleConfig.PREVIEW_ENABLED && !systemToHint && !mentionToHint) {
                String sender = senderName != null ? senderName.getString() : "";
                if (sender.isEmpty() && isSystem) sender = Text.translatable("opchat.sender.system").getString();
                String preview = sender.isEmpty() ? content.getString() : sender + ": " + content.getString();
                previews.add(new PreviewEntry(preview, PREVIEW_TICKS));
                while (previews.size() > ChatBubbleConfig.PREVIEW_LINES)
                    previews.remove(0);
                ChatBubbleHudOverlay.showNotification(preview);
            }
            if (systemToHint && !mentionToHint) {
                strongHintText = content.getString();
                strongHintTicks = STRONG_HINT_DURATION;
                strongHintIsMention = false;
            }
        }
    }

    private static Text addUnderlineToClicks(Text original) {
        MutableText result = Text.empty();
        original.visit((style, text) -> {
            Style newStyle = style.getClickEvent() != null
                ? style.withUnderline(true)
                : style;
            result.append(Text.literal(text).styled(s -> newStyle));
            return Optional.<Object>empty();
        }, Style.EMPTY);
        return result;
    }

    public static List<ChatMessage> getMessages() {
        return messages;
    }

    public static int getUnreadCount() {
        return unreadCount;
    }

    public static void markAllRead() {
        unreadCount = 0;
    }

    public static void setScreenOpen(boolean open) {
        screenOpen = open;
        if (open) {
            unreadCount = 0;
            ChatBubbleHudOverlay.dismissNotification();
        }
    }

    public static boolean isScreenOpen() {
        return screenOpen;
    }

    public static boolean hasUnreadMention(String playerName) {
        if (playerName == null || playerName.isEmpty()) return false;
        for (int i = messages.size() - unreadCount; i < messages.size(); i++) {
            if (i < 0) continue;
            String text = messages.get(i).content().getString();
            if (text.contains("@" + playerName)) return true;
        }
        return false;
    }

    public static Text quoteMessage(int index) {
        if (index < 0 || index >= messages.size()) return Text.literal("");
        ChatMessage msg = messages.get(index);
        MutableText quote = Text.literal("> " + msg.senderName().getString() + ": ");
        quote.append(msg.content());
        return quote;
    }

    public static ChatMessage getMessageAt(int index) {
        if (index < 0 || index >= messages.size()) return null;
        return messages.get(index);
    }

    public static void setPendingReply(String content, String sender) {
        pendingReplyContent = content;
        pendingReplySender = sender;
    }

    public static List<PreviewEntry> getPreviews() {
        return previews.isEmpty() ? null : previews;
    }

    public static void tickPreview() {
        var it = previews.iterator();
        while (it.hasNext()) {
            PreviewEntry e = it.next();
            if (--e.ticks <= 0) it.remove();
        }
    }

    public static String getStrongHintText() { return strongHintTicks > 0 ? strongHintText : null; }

    public static boolean isStrongHintMention() { return strongHintIsMention; }

    public static int getStrongHintTicks() { return strongHintTicks; }

    public static void tickStrongHint() { if (strongHintTicks > 0) strongHintTicks--; }

    public static int size() {
        return messages.size();
    }

    public static String getCustomTitle() {
        if (currentWorldKey == null) return null;
        loadWorldTitles();
        String v = worldTitles.get(currentWorldKey);
        return (v != null && !v.isEmpty()) ? v : null;
    }

    public static void setCustomTitle(String title) {
        if (currentWorldKey == null) return;
        loadWorldTitles();
        String v = (title != null && !title.isEmpty()) ? title : "";
        if (v.isEmpty())
            worldTitles.remove(currentWorldKey);
        else
            worldTitles.put(currentWorldKey, v);
        saveWorldTitles();
    }

    public static void setCurrentWorld(String name) {
        if (java.util.Objects.equals(name, currentWorldKey)) return;
        boolean wasFallback = "world".equals(currentWorldKey);
        boolean isSpecific = name != null && (name.startsWith("SP:") || name.startsWith("MP:"));
        boolean isRefinement = wasFallback && isSpecific;
        boolean hasPendingMessages = currentWorldKey == null && isSpecific && !messages.isEmpty();
        saveCurrentHistory();
        currentWorldKey = name;
        if (isRefinement || hasPendingMessages) {
            loadCurrentHistory();
            saveCurrentHistory();
            return;
        }
        messages.clear();
        unreadCount = 0;
        previews.clear();
        loadCurrentHistory();
    }

    public static void setHistorySavingEnabled(boolean enabled) {
        if (enabled) {
            loadCurrentHistory();
            saveCurrentHistory();
        }
    }

    private static Path getHistoryFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("opchat/chat_history.json");
    }

    private static void loadHistories() {
        if (historiesLoaded) return;
        historiesLoaded = true;
        Path file = getHistoryFile();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, List<PersistedMessage>> data = GSON.fromJson(reader,
                new TypeToken<Map<String, List<PersistedMessage>>>(){}.getType());
            if (data != null) savedHistories.putAll(data);
        } catch (Exception ignored) {}
    }

    private static void loadCurrentHistory() {
        if (!ChatBubbleConfig.CHAT_HISTORY_SAVE || currentWorldKey == null || "world".equals(currentWorldKey)) return;
        loadHistories();
        List<PersistedMessage> saved = savedHistories.get(currentWorldKey);
        if (saved == null || saved.isEmpty()) return;
        List<ChatMessage> currentSession = new ArrayList<>(messages);
        Set<String> existing = new HashSet<>();
        for (ChatMessage message : currentSession) {
            existing.add(message.time() + "\u0000" + message.senderName().getString() + "\u0000" + message.content().getString());
        }
        messages.clear();
        for (PersistedMessage item : saved) {
            try {
                String identity = item.time() + "\u0000" + item.senderName() + "\u0000" + item.content();
                if (existing.contains(identity)) continue;
                messages.add(new ChatMessage(
                    UUID.fromString(item.senderUUID()),
                    Text.literal(item.senderName()),
                    Text.literal(item.content()),
                    LocalTime.parse(item.time()),
                    item.isOwn(), item.isSystem(),
                    item.replyContent(), item.replySender(), item.messageHash(),
                    Math.max(1, item.duplicateCount())
                ));
            } catch (Exception ignored) {}
        }
        messages.addAll(currentSession);
        while (messages.size() > MAX) messages.remove(0);
    }

    private static void saveCurrentHistory() {
        if (!ChatBubbleConfig.CHAT_HISTORY_SAVE || currentWorldKey == null || "world".equals(currentWorldKey)) return;
        loadHistories();
        List<PersistedMessage> data = new ArrayList<>();
        for (ChatMessage message : messages) {
            data.add(new PersistedMessage(
                message.senderUUID().toString(), message.senderName().getString(),
                message.content().getString(), message.time().toString(),
                message.isOwn(), message.isSystem(), message.replyContent(),
                message.replySender(), message.messageHash(), message.duplicateCount()
            ));
        }
        savedHistories.put(currentWorldKey, data);
        Path file = getHistoryFile();
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(savedHistories, writer);
            }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}
    }

    private static Path getTitlesFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("opchat/titles.json");
    }

    private static void loadWorldTitles() {
        if (titlesLoaded) return;
        titlesLoaded = true;
        Path f = getTitlesFile();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Map<String, String> data = GSON.fromJson(r, new TypeToken<Map<String, String>>(){}.getType());
            if (data != null) worldTitles.putAll(data);
        } catch (Exception ignored) {}
    }

    private static void saveWorldTitles() {
        Path f = getTitlesFile();
        try {
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(worldTitles, w);
            }
        } catch (Exception ignored) {}
    }

    public static void applyChatMeta(UUID senderUUID, String messageHash, String quoteSender,
                                      String quoteContent, List<String> mentionTargets) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg.messageHash().equals(messageHash) && msg.senderUUID().equals(senderUUID)) {
                if (!quoteContent.isEmpty()) {
                    messages.set(i, new ChatMessage(
                        msg.senderUUID(), msg.senderName(), msg.content(), msg.time(),
                        msg.isOwn(), msg.isSystem(), quoteContent, quoteSender, msg.messageHash(),
                        msg.duplicateCount()));
                }
                return;
            }
        }
        if (!quoteContent.isEmpty()) {
            pendingMetas.put(messageHash, new PendingMeta(senderUUID, quoteSender, quoteContent, mentionTargets));
        }
    }
}
