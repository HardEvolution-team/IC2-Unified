package com.ded.ic2unified.core;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * IC2 Classic addon loader.
 * <p>
 * Addons written for IC2 Classic reference {@code ic2.*} / mod id {@code ic2}, which after relocation belongs to IC2 Experimental.
 * Jars put into {@code <game dir>/ic2classic-addons/} (instead of {@code mods/}) are rewritten by {@link AddonRemapper} into
 * {@code <game dir>/ic2unified/addon-cache/} and added to the class path, where FML discovers them like any other mod.
 * Results are cached by content hash, so only new or changed addons are processed.
 */
@IFMLLoadingPlugin.Name("IC2 Unified")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
public class UnifiedLoadingPlugin implements IFMLLoadingPlugin {

    private static final Logger LOGGER = LogManager.getLogger("ic2unified-addons");
    public static final String ADDON_DIR = "ic2classic-addons";

    @Override
    public void injectData(Map<String, Object> data) {
        Object loc = data.get("mcLocation");
        File gameDir = loc instanceof File ? (File) loc : new File(".");
        try {
            loadClassic(gameDir.toPath());
        } catch (Throwable t) {
            LOGGER.error("Failed to relocate IC2 Classic", t);
        }
        try {
            loadAddons(gameDir.toPath());
        } catch (Throwable t) {
            LOGGER.error("Failed to load IC2 Classic addons", t);
        }
    }

    /** Relocated Classic jar produced at runtime (null = none). */
    static Path relocatedClassic;

    private static boolean isClassic(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            return jf.getEntry("ic2/core/IC2.class") != null && jf.stream().anyMatch(e -> e.getName().startsWith("ic2/core/platform/"));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isExperimental(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            return jf.getEntry("ic2/core/IC2.class") != null && jf.stream().noneMatch(e -> e.getName().startsWith("ic2/core/platform/"));
        } catch (IOException e) {
            return false;
        }
    }

    private static List<Path> jars(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.{jar,zip}")) {
            ds.forEach(out::add);
        } catch (IOException ignored) {
        }
        out.sort(null);
        return out;
    }

    /**
     * The ORIGINAL IC2 Classic jar is placed by the player into ic2classic-addons/ (never mods/). It is relocated
     * to the ic2classic namespace on the player's machine, cached by hash and added to the class path.
     */
    private static void loadClassic(Path gameDir) throws Exception {
        Path addonDir = Path.of(System.getProperty("ic2unified.addonsDir", gameDir.resolve(ADDON_DIR).toString()));
        Path mods = gameDir.resolve("mods");
        for (Path p : jars(mods)) {
            if (isClassic(p)) LOGGER.error("IC2 Classic {} is in mods/ - it conflicts with IC2 Experimental! Move it to {}", p.getFileName(), addonDir);
        }
        Path classic = null;
        for (Path p : jars(addonDir)) if (isClassic(p)) { classic = p; break; }
        if (classic == null) {
            LOGGER.info("No IC2 Classic jar in {} - running without IC2 Classic", addonDir);
            return;
        }
        Path exp = null;
        for (Path p : jars(mods)) if (isExperimental(p)) { exp = p; break; }
        Path cacheDir = gameDir.resolve("ic2unified").resolve("classic-cache");
        Files.createDirectories(cacheDir);
        String h = hash(classic) + (exp != null ? "-" + hash(exp) : "");
        Path out = cacheDir.resolve("IC2Classic-relocated-" + h + ".jar");
        if (!Files.exists(out)) {
            Path tmp = cacheDir.resolve(out.getFileName() + ".tmp");
            int n = ClassicRelocator.relocate(classic, tmp, exp);
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Relocated IC2 Classic {} ({} classes) -> {}", classic.getFileName(), n, out.getFileName());
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(cacheDir)) {
            for (Path p : ds) if (!p.equals(out)) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
        Launch.classLoader.addURL(out.toUri().toURL());
        relocatedClassic = out;
        LOGGER.info("Loaded relocated IC2 Classic from {}", out);
    }

    private static void loadAddons(Path gameDir) throws Exception {
        Path addonDir = Path.of(System.getProperty("ic2unified.addonsDir", gameDir.resolve(ADDON_DIR).toString()));
        if (!Files.isDirectory(addonDir)) {
            Files.createDirectories(addonDir);
            return;
        }
        List<Path> addons = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(addonDir, "*.{jar,zip}")) {
            ds.forEach(p -> { if (!isClassic(p)) addons.add(p); });
        }
        if (addons.isEmpty()) return;
        addons.sort(null);

        Path cacheDir = gameDir.resolve("ic2unified").resolve("addon-cache");
        Files.createDirectories(cacheDir);
        AddonRemapper remapper = new AddonRemapper(findClassicClasses(gameDir.resolve("mods")));
        Set<Path> keep = new HashSet<>();

        for (Path addon : addons) {
            if (isCoreMod(addon)) {
                LOGGER.warn("{} is a coremod - coremod addons are not supported, put it into mods/ if it does not need IC2 Classic", addon.getFileName());
            }
            String name = addon.getFileName().toString().replaceAll("\\.(jar|zip)$", "");
            Path out = cacheDir.resolve("ic2c-" + name + "-" + hash(addon) + ".jar");
            keep.add(out);
            if (!Files.exists(out)) {
                Path tmp = cacheDir.resolve(out.getFileName() + ".tmp");
                int n = remapper.remap(addon, tmp);
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Remapped IC2 Classic addon {} ({} classes touched)", addon.getFileName(), n);
            }
            Launch.classLoader.addURL(out.toUri().toURL());
            LOGGER.info("Loaded IC2 Classic addon {}", addon.getFileName());
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(cacheDir)) {
            for (Path p : ds) if (!keep.contains(p)) Files.deleteIfExists(p);
        } catch (IOException e) {
            LOGGER.debug("Could not clean addon cache", e);
        }
    }

    /** Class names of IC2 Classic, read from the relocated jar in mods/, so reflective names like "ic2.core.IC2" can be told from lang keys. */
    private static Set<String> findClassicClasses(Path modsDir) {
        if (relocatedClassic != null) {
            try {
                return AddonRemapper.readClassicClasses(relocatedClassic);
            } catch (IOException ignored) {
            }
        }
        if (Files.isDirectory(modsDir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(modsDir, "*.jar")) {
                for (Path p : ds) {
                    try (JarFile jar = new JarFile(p.toFile())) {
                        if (jar.getEntry("ic2classic/core/IC2.class") != null) return AddonRemapper.readClassicClasses(p);
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }
        LOGGER.warn("Relocated IC2 Classic not found in mods/ - reflective class names in addons will not be rewritten");
        return Collections.emptySet();
    }

    private static boolean isCoreMod(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            Manifest mf = jf.getManifest();
            return mf != null && mf.getMainAttributes().getValue("FMLCorePlugin") != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static String hash(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update((byte) AddonRemapper.VERSION);
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            for (int r; (r = is.read(buf)) > 0; ) md.update(buf, 0, r);
        }
        return HexFormat.of().formatHex(md.digest(), 0, 6);
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
