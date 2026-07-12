package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.ChatMessageStore;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ChatMetaPacket(UUID senderUUID, String messageHash, String quoteSender,
                              String quoteContent, List<String> mentionTargets)
        implements CustomPayload {

    public static final Id<ChatMetaPacket> ID = new Id<>(Identifier.of(ChatBubbleMod.MODID, "chat_meta"));

    public static final PacketCodec<PacketByteBuf, ChatMetaPacket> CODEC =
        PacketCodec.of(
            (pkt, buf) -> {
                buf.writeUuid(pkt.senderUUID);
                buf.writeString(pkt.messageHash);
                buf.writeString(pkt.quoteSender);
                buf.writeString(pkt.quoteContent);
                buf.writeInt(pkt.mentionTargets.size());
                for (String target : pkt.mentionTargets)
                    buf.writeString(target);
            },
            buf -> {
                UUID senderUUID = buf.readUuid();
                String messageHash = buf.readString();
                String quoteSender = buf.readString();
                String quoteContent = buf.readString();
                int count = buf.readInt();
                List<String> mentionTargets = new ArrayList<>(count);
                for (int i = 0; i < count; i++)
                    mentionTargets.add(buf.readString());
                return new ChatMetaPacket(senderUUID, messageHash, quoteSender, quoteContent, mentionTargets);
            }
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            context.client().execute(() ->
                ChatMessageStore.applyChatMeta(
                    payload.senderUUID, payload.messageHash,
                    payload.quoteSender, payload.quoteContent, payload.mentionTargets)
            );
        });
    }
}
