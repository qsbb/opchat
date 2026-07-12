package com.opchat.packets;

import com.opchat.ChatBubbleMod;
import com.opchat.ChatServerListener;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record QuoteSyncPacket(String quotedSenderName, String quotedContent, String messageHash)
        implements CustomPayload {

    public static final Id<QuoteSyncPacket> ID = new Id<>(Identifier.of(ChatBubbleMod.MODID, "quote_sync"));

    public static final PacketCodec<PacketByteBuf, QuoteSyncPacket> CODEC =
        PacketCodec.of(
            (pkt, buf) -> {
                buf.writeString(pkt.quotedSenderName);
                buf.writeString(pkt.quotedContent);
                buf.writeString(pkt.messageHash);
            },
            buf -> new QuoteSyncPacket(buf.readString(), buf.readString(), buf.readString())
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void send(String quotedSenderName, String quotedContent, String messageText) {
        String hash = String.valueOf(messageText.hashCode());
        ClientPlayNetworking.send(new QuoteSyncPacket(quotedSenderName, quotedContent, hash));
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            context.server().execute(() -> {
                var sender = context.player();
                if (sender != null) {
                    ChatServerListener.onQuoteReceived(
                        sender.getUuid(), payload.quotedSenderName, payload.quotedContent, payload.messageHash);
                }
            });
        });
    }
}
