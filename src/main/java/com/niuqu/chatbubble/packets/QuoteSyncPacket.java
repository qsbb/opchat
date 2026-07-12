package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.ChatServerListener;
import com.niuqu.chatbubble.NetworkHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QuoteSyncPacket(String quotedSenderName, String quotedContent, String messageHash)
        implements CustomPacketPayload {

    public static final Type<QuoteSyncPacket> TYPE = new Type<>(NetworkHandler.QUOTE_SYNC_ID);

    public static final StreamCodec<FriendlyByteBuf, QuoteSyncPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.quotedSenderName);
                buf.writeUtf(pkt.quotedContent);
                buf.writeUtf(pkt.messageHash);
            },
            buf -> new QuoteSyncPacket(buf.readUtf(), buf.readUtf(), buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(String quotedSenderName, String quotedContent, String messageText) {
        String hash = String.valueOf(messageText.hashCode());
        ClientPlayNetworking.send(new QuoteSyncPacket(quotedSenderName, quotedContent, hash));
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var sender = context.player();
                if (sender != null) {
                    ChatServerListener.onQuoteReceived(
                        sender.getUUID(), payload.quotedSenderName, payload.quotedContent, payload.messageHash);
                }
            });
        });
    }
}
