package top.mc100.qdshop.extension;

import org.bukkit.entity.Player;
import java.util.Objects;

/** 调用发生在服务器主线程；viewer 是打开商店的人，添加预览时是设置者。 */
public record ExtensionContext(Player viewer, String shopId) {
    public ExtensionContext { Objects.requireNonNull(viewer); Objects.requireNonNull(shopId); }
}
