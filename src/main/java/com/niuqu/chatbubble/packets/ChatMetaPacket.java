package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.NetworkHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ChatMetaPacket(UUID senderUUID, String messageHash, String quoteSender,
                              String quoteContent, List<String> mentionTargets)
        implements CustomPacketPayload {

    public static final Type<ChatMetaPacket> TYPE = new Type<>(NetworkHandler.CHAT_META_ID);

    public static final StreamCodec<FriendlyByteBuf, ChatMetaPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUUID(pkt.senderUUID);
                buf.writeUtf(pkt.messageHash);
                buf.writeUtf(pkt.quoteSender);
                buf.writeUtf(pkt.quoteContent);
                buf.writeInt(pkt.mentionTargets.size());
                for (String target : pkt.mentionTargets)
                    buf.writeUtf(target);
            },
            buf -> {
                UUID senderUUID = buf.readUUID();
                String messageHash = buf.readUtf();
                String quoteSender = buf.readUtf();
                String quoteContent = buf.readUtf();
                int count = buf.readInt();
                List<String> mentionTargets = new ArrayList<>(count);
                for (int i = 0; i < count; i++)
                    mentionTargets.add(buf.readUtf());
                return new ChatMetaPacket(senderUUID, messageHash, quoteSender, quoteContent, mentionTargets);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            context.client().execute(() ->
                ChatMessageStore.applyChatMeta(
                    payload.senderUUID, payload.messageHash,
                    payload.quoteSender, payload.quoteContent, payload.mentionTargets)
            );
        });
    }
}
