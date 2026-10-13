package top.mc100.qdshop.extension;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.*;
import java.util.List;
import java.util.jar.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtensionRegistryTest {
    @TempDir Path dir;

    void compileJar(Path source, String provider, Path jar) throws Exception {
        Path classes = Files.createTempDirectory(dir, "classes-");
        int code = ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "25", "-classpath",
                System.getProperty("java.class.path"), "-d", classes.toString(), source.toString());
        assertEquals(0, code);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar)); var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                output.write(Files.readAllBytes(file)); output.closeEntry();
            }
            output.putNextEntry(new JarEntry(ExtensionRegistry.PROVIDER_FILE));
            output.write((provider + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)); output.closeEntry();
        }
    }
    Path fixtureJar(String filename, String id) throws Exception {
        Path sourceDir = Files.createTempDirectory(dir, "source-");
        Path source = sourceDir.resolve("Fixture.java");
        Files.writeString(source, """
            package example;
            public final class Fixture extends top.mc100.qdshop.extension.ExtensionItem {
                public Fixture() { super("%s", "Test description", "Test author"); }
                public org.bukkit.inventory.ItemStack createItem(top.mc100.qdshop.extension.ExtensionContext c, java.util.List<String> a) { return null; }
            }
            """.formatted(id));
        Path jar = dir.resolve(filename); compileJar(source, "example.Fixture", jar); return jar;
    }

    @Test void jarScanRejectsDuplicatesReloadsAndUnloadsDeletedFilesWhileRetainingApiRegistrations() throws Exception {
        fixtureJar("a.jar", "fixture"); fixtureJar("b.jar", "fixture");
        try (var registry = new ExtensionRegistry(dir, mock(Logger.class))) {
            registry.register(new ExtensionItem("direct", "Description", "Author") {
                public ItemStack createItem(ExtensionContext c, List<String> args) { return null; }
            });
            registry.reload();
            assertEquals(List.of("direct", "fixture"), registry.list().stream().map(ExtensionItem::getId).toList());
            assertEquals(List.of("b.jar"), registry.getFailures());
            assertEquals("Test author", registry.get("fixture").getAuthor());
            ClassLoader firstLoader = registry.get("fixture").getClass().getClassLoader();
            registry.reload();
            assertNotSame(firstLoader, registry.get("fixture").getClass().getClassLoader());
            Files.delete(dir.resolve("a.jar")); Files.delete(dir.resolve("b.jar"));
            registry.reload();
            assertNull(registry.get("fixture")); assertNotNull(registry.get("direct"));
            assertTrue(registry.getFailures().isEmpty());
        }
    }

    @Test void malformedJarDoesNotBlockValidJar() throws Exception {
        fixtureJar("valid.jar", "fixture"); Files.writeString(dir.resolve("broken.jar"), "not a JAR");
        try (var registry = new ExtensionRegistry(dir, mock(Logger.class))) {
            registry.reload(); assertNotNull(registry.get("fixture"));
            assertEquals(List.of("broken.jar"), registry.getFailures());
        }
    }

    @Test void realExampleJarBuildsHeadsUsingEachViewersProfileAndValidatesArguments() throws Exception {
        compileJar(Path.of("examples/player-skull/src/main/java/top/mc100/qdshop/example/PlayerSkullExtension.java"),
                "top.mc100.qdshop.example.PlayerSkullExtension", dir.resolve("player-skull.jar"));
        Player first = mock(Player.class), second = mock(Player.class);
        var firstProfile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        var secondProfile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(first.getPlayerProfile()).thenReturn(firstProfile); when(second.getPlayerProfile()).thenReturn(secondProfile);
        try (var registry = new ExtensionRegistry(dir, mock(Logger.class));
             var items = mockConstruction(ItemStack.class, (item, context) -> {
                 SkullMeta meta = mock(SkullMeta.class);
                 when(item.getItemMeta()).thenReturn(meta); when(item.getType()).thenReturn(mock(Material.class));
                 when(item.getAmount()).thenReturn((Integer) context.arguments().get(1));
                 when(item.getMaxStackSize()).thenReturn(64); when(item.clone()).thenReturn(item);
             })) {
            registry.reload();
            var one = registry.createItem("player_skull", new ExtensionContext(first, "shop"), List.of("2"));
            var two = registry.createItem("player_skull", new ExtensionContext(second, "shop"), List.of());
            verify((SkullMeta) one.getItemMeta()).setPlayerProfile(firstProfile);
            verify((SkullMeta) two.getItemMeta()).setPlayerProfile(secondProfile);
            assertEquals(2, one.getAmount()); assertEquals(1, two.getAmount());
            assertThrows(IllegalArgumentException.class, () -> registry.createItem("player_skull", new ExtensionContext(first, "shop"), List.of("65")));
            assertThrows(IllegalArgumentException.class, () -> registry.createItem("missing", new ExtensionContext(first, "shop"), List.of()));
        }
    }
}
