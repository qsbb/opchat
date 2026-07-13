package com.opchat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

public class AvatarHelper {
    private static final Map<UUID, Identifier> skinCache = new HashMap<>();
    private static final Map<String, UUID> nameToUuidCache = new HashMap<>();

    public static void renderSkin(DrawContext context, UUID uuid, int x, int y, int size) {
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier skin = null;
        if (client.getNetworkHandler() != null) {
            var entry = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) {
                skin = entry.getSkinTextures().body().texturePath();
                if (skin != null) skinCache.put(uuid, skin);
            }
        }
        if (skin == null) skin = skinCache.get(uuid);
        if (skin == null) skin = Identifier.of("textures/entity/player/slim/steve.png");
        context.drawTexture(RenderPipelines.GUI_TEXTURED, skin, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, skin, x, y, 40.0f, 8.0f, size, size, 8, 8, 64, 64);
    }

    public static void renderSkin(DrawContext context, UUID uuid, int x, int y) {
        renderSkin(context, uuid, x, y, 24);
    }

    public static UUID getUuidForName(String name) {
        if (name == null || name.isEmpty()) return null;
        UUID cached = nameToUuidCache.get(name);
        if (cached != null) return cached;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            for (var entry : client.getNetworkHandler().getPlayerList()) {
                String n = entry.getProfile().name();
                if (name.equals(n)) {
                    UUID id = entry.getProfile().id();
                    if (id != null) {
                        nameToUuidCache.put(name, id);
                        return id;
                    }
                }
            }
        }
        return null;
    }
}
