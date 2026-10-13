package top.mc100.qdshop.example;

import top.mc100.qdshop.extension.ExtensionContext;
import top.mc100.qdshop.extension.ExtensionItem;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.List;

/** /qdshop addex <id> player_skull [数量]，输出当前打开商店的玩家的头颅。 */
public final class PlayerSkullExtension extends ExtensionItem {
    public PlayerSkullExtension() {
        super("player_skull", "生成打开商店的玩家的头颅，可选参数为数量（1-64）", "AWAFAI, QIDIAN SERVER");
    }
    @Override public ItemStack createItem(ExtensionContext context, List<String> arguments) {
        if (arguments.size() > 1) throw new IllegalArgumentException("player_skull 用法：[数量 1-64]");
        int amount = 1;
        if (!arguments.isEmpty()) {
            try { amount = Integer.parseInt(arguments.getFirst()); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("头颅数量必须为 1-64 的整数"); }
        }
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("头颅数量必须为 1-64 的整数");
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, amount);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setPlayerProfile(context.viewer().getPlayerProfile());
        head.setItemMeta(meta);
        return head;
    }
    @Override public List<String> completeArguments(CommandSender sender, List<String> arguments) {
        return arguments.size() == 1 ? List.of("1", "8", "16", "32", "64") : List.of();
    }
}
