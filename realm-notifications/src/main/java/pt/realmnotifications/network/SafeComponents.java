package pt.realmnotifications.network;

import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import pt.realmnotifications.domain.NotificationRules;

/** A small presentational tree, never commands, selectors, NBT, hover data or styles. */
public final class SafeComponents {
    private SafeComponents() {}

    public static Component sanitize(Component source, int textLimit) {
        if (source == null) throw new IllegalArgumentException("Missing notification component");
        Component safe = copy(source, new Budget(textLimit), 0);
        if (ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, safe).getOrThrow().toString().length() > NotificationRules.JSON_LIMIT)
            throw new IllegalArgumentException("Notification component exceeds its wire limit");
        return safe;
    }

    private static MutableComponent copy(Component source, Budget budget, int depth) {
        if (depth > NotificationRules.MAX_DEPTH || ++budget.nodes > NotificationRules.MAX_NODES)
            throw new IllegalArgumentException("Notification tree is too complex");
        MutableComponent result;
        if (source.getContents() instanceof PlainTextContents plain) {
            result = Component.literal(budget.text(plain.text()));
        } else if (source.getContents() instanceof TranslatableContents translated) {
            String key = translated.getKey();
            if (!NotificationRules.translationKey(key)) throw new IllegalArgumentException("Invalid translation key");
            Object[] args = translated.getArgs();
            if (args.length > 16) throw new IllegalArgumentException("Too many translation arguments");
            Object[] safe = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object argument = args[i];
                if (argument instanceof Component component) safe[i] = copy(component, budget, depth + 1);
                else if (argument instanceof String || argument instanceof Number || argument instanceof Boolean)
                    safe[i] = budget.text(String.valueOf(argument));
                else throw new IllegalArgumentException("Unsupported translation argument");
            }
            String fallback = translated.getFallback();
            result = fallback == null ? Component.translatable(key, safe)
                    : Component.translatableWithFallback(key, budget.text(fallback), safe);
        } else {
            // Dynamic selectors/scores/NBT are not evaluated by this presentational channel.
            throw new IllegalArgumentException("Unsupported notification content");
        }
        for (Component sibling : source.getSiblings()) result.append(copy(sibling, budget, depth + 1));
        return result;
    }

    private static final class Budget {
        int remaining;
        int nodes;
        Budget(int limit) { remaining = limit; }
        String text(String value) {
            String result = NotificationRules.text(value, remaining);
            remaining -= result.codePointCount(0, result.length());
            return result;
        }
    }
}
