package com.mrsuffix.breakallblocks.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utility class for parsing and sending MiniMessage-formatted messages.
 *
 * <p>All player-facing messages use the Adventure MiniMessage format
 * ({@code <red>}, {@code <bold>}, {@code <gradient:...>}, etc.).
 * Legacy &amp;-codes are NOT supported intentionally.</p>
 */
public final class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MessageUtil() {}

    /**
     * Parse a MiniMessage string and send it to a {@link CommandSender}.
     * Replaces {@code {prefix}} with the supplied prefix before parsing.
     */
    public static void sendParsed(CommandSender sender, String template, String prefix) {
        String resolved = template.replace("{prefix}", prefix);
        Component component = MM.deserialize(resolved);
        sender.sendMessage(component);
    }

    /**
     * Broadcast a MiniMessage-formatted message to all online players.
     */
    public static void broadcastParsed(String template, String prefix) {
        String resolved = template.replace("{prefix}", prefix);
        Component component = MM.deserialize(resolved);
        Bukkit.getServer().broadcast(component);
    }

    /**
     * Parse a raw MiniMessage string without prefix substitution.
     */
    public static Component parse(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    /**
     * Convenience: send to a CommandSender without prefix replacement.
     */
    public static void send(CommandSender sender, String miniMessage) {
        sender.sendMessage(MM.deserialize(miniMessage));
    }
}
