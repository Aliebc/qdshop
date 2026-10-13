package top.mc100.qdshop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** MiniMessage 文本工具。 */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {}

    /** 把 "&a" 之类的旧式颜色码或 MiniMessage 文本转成 Component。 */
    public static Component mm(String text) {
        if (text == null) return Component.empty();
        // 兼容 & 颜色码
        String t = text.replace('&', '\u00A7');
        try {
            return MM.deserialize(t);
        } catch (Exception e) {
            return Component.text(text);
        }
    }
}
