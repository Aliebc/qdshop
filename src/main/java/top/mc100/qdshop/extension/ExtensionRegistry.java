package top.mc100.qdshop.extension;

import top.mc100.qdshop.model.Trade;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/** API 注册器及独立 JAR 加载器。所有注册、重载和生成操作均在服务器主线程调用。 */
public final class ExtensionRegistry implements AutoCloseable {
    public static final String PROVIDER_FILE = "META-INF/services/" + ExtensionItem.class.getName();
    private final Path directory;
    private final Logger logger;
    private final Map<String, ExtensionItem> direct = new LinkedHashMap<>();
    private final Map<String, ExtensionItem> installed = new LinkedHashMap<>();
    private final List<URLClassLoader> loaders = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();

    public ExtensionRegistry(Path directory, Logger logger) throws IOException {
        this.directory = directory;
        this.logger = logger;
        Files.createDirectories(directory);
    }
    /** 其他插件可通过 QdShopPlugin#getExtensionRegistry() 注册自己的拓展。重复 id 会拒绝。 */
    public void register(ExtensionItem item) {
        Objects.requireNonNull(item);
        if (get(item.getId()) != null) throw new IllegalArgumentException("拓展 id 重复：" + item.getId());
        try { item.onEnable(); }
        catch (RuntimeException | LinkageError e) { disable(item); throw e; }
        direct.put(item.getId(), item);
    }
    public ExtensionItem get(String id) { return id == null ? null : direct.containsKey(id) ? direct.get(id) : installed.get(id); }
    public List<ExtensionItem> list() {
        List<ExtensionItem> result = new ArrayList<>(direct.values()); result.addAll(installed.values());
        return result.stream().sorted(Comparator.comparing(ExtensionItem::getId)).toList();
    }
    public List<String> getFailures() { return List.copyOf(failures); }
    public Path getDirectory() { return directory; }
    public ItemStack createItem(String id, ExtensionContext context, List<String> arguments) {
        ExtensionItem extension = get(id);
        if (extension == null) throw new IllegalArgumentException("拓展未安装：" + id);
        ItemStack result = extension.createItem(context, List.copyOf(arguments));
        if (!Trade.validItem(result)) throw new IllegalArgumentException("拓展 " + id + " 返回了无效物品或数量");
        return result.clone();
    }
    /** 重新扫描目录，仅卸载 JAR 拓展，保留其他插件通过 API 直接注册的拓展。 */
    public void reload() throws IOException {
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted().toList();
        }
        unloadJars(); failures.clear();
        for (Path file : files) loadJar(file);
    }
    private void loadJar(Path file) {
        URLClassLoader loader = null;
        List<ExtensionItem> loaded = new ArrayList<>();
        List<ExtensionItem> enabled = new ArrayList<>();
        try (JarFile jar = new JarFile(file.toFile())) {
            var descriptor = jar.getJarEntry(PROVIDER_FILE);
            if (descriptor == null) throw new IllegalArgumentException("JAR 缺少 " + PROVIDER_FILE);
            loader = new URLClassLoader(new java.net.URL[]{file.toUri().toURL()}, ExtensionItem.class.getClassLoader());
            Set<String> ids = new HashSet<>();
            String providers;
            try (InputStream input = jar.getInputStream(descriptor)) { providers = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            for (String raw : providers.split("\\R")) {
                String className = raw.split("#", 2)[0].trim();
                if (className.isEmpty()) continue;
                ExtensionItem item = Class.forName(className, true, loader).asSubclass(ExtensionItem.class).getConstructor().newInstance();
                if (get(item.getId()) != null || !ids.add(item.getId())) throw new IllegalArgumentException("拓展 id 重复：" + item.getId());
                loaded.add(item);
            }
            if (loaded.isEmpty()) throw new IllegalArgumentException("JAR 未声明任何拓展物品");
            for (ExtensionItem item : loaded) { enabled.add(item); item.onEnable(); }
            for (ExtensionItem item : loaded) installed.put(item.getId(), item);
            loaders.add(loader);
        } catch (Exception | LinkageError e) {
            for (ExtensionItem item : enabled) disable(item);
            if (loader != null) try { loader.close(); } catch (IOException ignored) { }
            failures.add(file.getFileName().toString());
            logger.log(Level.WARNING, "加载拓展失败：" + file.getFileName(), e);
        }
    }
    private void disable(ExtensionItem item) {
        try { item.onDisable(); }
        catch (Exception | LinkageError e) { logger.log(Level.WARNING, "卸载拓展失败：" + item.getId(), e); }
    }
    private void unloadJars() {
        installed.values().forEach(this::disable); installed.clear();
        for (URLClassLoader loader : loaders) try { loader.close(); } catch (IOException e) { logger.log(Level.WARNING, "关闭拓展 JAR 失败", e); }
        loaders.clear();
    }
    @Override public void close() { unloadJars(); direct.values().forEach(this::disable); direct.clear(); failures.clear(); }
}
