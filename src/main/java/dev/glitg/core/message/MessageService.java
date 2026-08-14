package dev.glitg.core.message;

import dev.glitg.core.config.ConfigService;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;

public final class MessageService {
    private final ConfigService configs;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageService(ConfigService configs) {
        this.configs = configs;
    }

    public Component component(String key, Map<String, ?> placeholders) {
        String prefix = configs.file("messages.yml").getString("prefix", "");
        String template = configs.file("messages.yml").getString(key, "<red>Missing message: " + key + "</red>");
        for (var entry : placeholders.entrySet()) {
            template = template.replace("<" + entry.getKey() + ">", escape(String.valueOf(entry.getValue())));
        }
        return miniMessage.deserialize(InterfaceTheme.apply(prefix + template));
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    public void send(Audience audience, String key) {
        audience.sendMessage(component(key));
    }

    public void send(Audience audience, String key, Map<String, ?> placeholders) {
        audience.sendMessage(component(key, placeholders));
    }

    public Component raw(String miniMessageText) {
        return miniMessage.deserialize(InterfaceTheme.apply(miniMessageText));
    }

    private static String escape(String value) {
        return value.replace("<", "&lt;").replace(">", "&gt;");
    }
}
