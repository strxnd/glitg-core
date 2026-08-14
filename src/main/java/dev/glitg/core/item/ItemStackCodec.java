package dev.glitg.core.item;

import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.Base64;

public final class ItemStackCodec {
    private ItemStackCodec() {}

    public static String encode(ItemStack stack) {
        return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    public static ItemStack decode(String encoded) throws IOException {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid ItemStack payload", exception);
        }
    }
}
