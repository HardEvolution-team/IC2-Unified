package com.ded.ic2unified.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Rewrites an IC2 Classic <b>addon</b> jar so it talks to the relocated IC2 Classic ({@code ic2classic}) instead of {@code ic2}.
 * Mirrors the rules of {@code ClassicRelocator}, but works from the addon's side:
 * <ul>
 *     <li>every type reference {@code ic2/**} becomes {@code ic2classic/**} (an addon for Classic can only mean Classic's classes);</li>
 *     <li>string constants: mod id {@code "ic2"}, dependency strings ({@code required-after:ic2}), resource locations
 *     ({@code ic2:...}), Classic fluid names, FML API names and reflective class names are rewritten;</li>
 *     <li>json / lang / mcmod.info resources get the same treatment;</li>
 *     <li>jar signatures are stripped (they would no longer match).</li>
 * </ul>
 * The addon's own classes and mod id are left untouched.
 */
public final class AddonRemapper {

    /** Bump when the rules change, invalidates the cache. */
    public static final int VERSION = 1;

    static final String OLD_ID = "ic2";
    static final String NEW_ID = "ic2classic";

    private static final Pattern RES_DOMAIN = Pattern.compile("(?<![A-Za-z0-9_.\\-])ic2:");
    private static final Pattern DEPENDENCY = Pattern.compile("(?<=(?:after|before|required|client|server):)ic2(?![A-Za-z0-9_])");
    private static final Pattern QUOTED_ID = Pattern.compile("\"ic2\"");
    private static final Pattern FLUID = Pattern.compile("(?<![A-Za-z0-9_])ic2(air|biogas|biomass|construction_foam|coolant|distilled_water|heavy_water"
            + "|hot_coolant|hot_water|hydrogen|oxygen|pahoehoe_lava|steam|superheated_steam|uu_matter|weed_ex)(?![A-Za-z0-9_])");
    private static final Map<String, String> EXACT = Map.of("ic2api", "ic2classicapi", "ic2audioapi", "ic2classicaudioapi");
    private static final Pattern CLASS_NAME = Pattern.compile("ic2(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    /** Internal names (ic2/...) of the original Classic classes and packages; empty = unknown, reflective names are then left alone. */
    private final Set<String> classicClasses;
    private final Set<String> classicPackages = new HashSet<>();

    public AddonRemapper(Set<String> classicClasses) {
        this.classicClasses = classicClasses;
        for (String c : classicClasses) {
            for (int i = c.lastIndexOf('/'); i > 0; i = c.lastIndexOf('/', i - 1)) classicPackages.add(c.substring(0, i));
        }
    }

    /**
     * Collects the class names of a relocated Classic jar ({@code ic2classic/**}) and returns them under their original names ({@code ic2/**}).
     */
    public static Set<String> readClassicClasses(Path relocatedClassic) throws IOException {
        Set<String> out = new HashSet<>();
        try (JarFile jar = new JarFile(relocatedClassic.toFile())) {
            for (JarEntry e : Collections.list(jar.entries())) {
                String n = e.getName();
                if (n.startsWith(NEW_ID + "/") && n.endsWith(".class")) {
                    out.add(OLD_ID + n.substring(NEW_ID.length(), n.length() - 6));
                }
            }
        }
        return out;
    }

    /** @return number of rewritten class files */
    public int remap(Path in, Path out) throws IOException {
        int changed = 0;
        try (JarFile jar = new JarFile(in.toFile())) {
            Manifest mf = jar.getManifest() != null ? new Manifest(jar.getManifest()) : new Manifest();
            mf.getEntries().clear(); // per-entry digests of the signature
            mf.getMainAttributes().putValue("Manifest-Version", "1.0");
            mf.getMainAttributes().putValue("IC2Unified-Addon-Remapped", Integer.toString(VERSION));
            Set<String> written = new HashSet<>();
            written.add("META-INF/MANIFEST.MF");
            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(out), mf)) {
                for (JarEntry e : Collections.list(jar.entries())) {
                    if (e.isDirectory()) continue;
                    String n = e.getName();
                    String upper = n.toUpperCase(Locale.ROOT);
                    if (upper.equals("META-INF/MANIFEST.MF") || upper.startsWith("META-INF/FML_CACHE")
                            || upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")) {
                        continue;
                    }
                    byte[] data;
                    try (InputStream is = jar.getInputStream(e)) {
                        data = is.readAllBytes();
                    }
                    if (n.endsWith(".class")) {
                        byte[] t = transformClass(data);
                        if (!Arrays.equals(t, data)) changed++;
                        data = t;
                    } else if (isText(n)) {
                        data = transformText(new String(data, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                    }
                    String newName = mapPath(n);
                    if (!written.add(newName)) continue;
                    jos.putNextEntry(new ZipEntry(newName));
                    jos.write(data);
                    jos.closeEntry();
                }
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------ mapping

    private static String mapPath(String n) {
        // an addon that shades (part of) the Classic API must ship it under the relocated name too
        if (n.startsWith(OLD_ID + "/")) return NEW_ID + n.substring(OLD_ID.length());
        return n;
    }

    private static boolean isText(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.endsWith(".json") || l.endsWith(".info") || l.endsWith(".mcmeta") || l.endsWith(".cfg");
    }

    private static String transformText(String text) {
        text = RES_DOMAIN.matcher(text).replaceAll(NEW_ID + ":");
        text = FLUID.matcher(text).replaceAll(NEW_ID + "_$1");
        text = QUOTED_ID.matcher(text).replaceAll("\"" + NEW_ID + "\""); // mcmod.info "dependencies": ["ic2"]
        return text;
    }

    static String mapInternal(String internal) {
        if (internal != null && (internal.equals(OLD_ID) || internal.startsWith(OLD_ID + "/"))) {
            return NEW_ID + internal.substring(OLD_ID.length());
        }
        return internal;
    }

    private boolean isClassicName(String internal) {
        if (classicClasses.isEmpty()) return false;
        int dollar = internal.indexOf('$');
        String outer = dollar >= 0 ? internal.substring(0, dollar) : internal;
        return classicClasses.contains(outer) || classicPackages.contains(internal);
    }

    String mapString(String s) {
        if (s.isEmpty() || !s.contains(OLD_ID)) return s;
        if (s.equals(OLD_ID)) return NEW_ID;
        if (EXACT.containsKey(s)) return EXACT.get(s);
        if (CLASS_NAME.matcher(s).matches()) {
            // only real classes/packages - "ic2.message.foo" may just as well be a translation key
            return isClassicName(s.replace('.', '/')) ? NEW_ID + s.substring(OLD_ID.length()) : s;
        }
        if (s.startsWith(OLD_ID + "/")) return mapInternal(s);
        if (s.startsWith("L" + OLD_ID + "/") && s.endsWith(";")) return "L" + mapInternal(s.substring(1));
        s = DEPENDENCY.matcher(s).replaceAll(NEW_ID);
        s = FLUID.matcher(s).replaceAll(NEW_ID + "_$1");
        s = RES_DOMAIN.matcher(s).replaceAll(NEW_ID + ":");
        if (s.startsWith("assets/" + OLD_ID + "/") || s.startsWith("/assets/" + OLD_ID + "/")) {
            s = s.replaceFirst("assets/" + OLD_ID + "/", "assets/" + NEW_ID + "/");
        }
        return s;
    }

    private byte[] transformClass(byte[] data) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassRemapper(cw, new Remapper() {
            @Override
            public String map(String internalName) {
                return mapInternal(internalName);
            }

            @Override
            public Object mapValue(Object value) {
                if (value instanceof String) return mapString((String) value);
                return super.mapValue(value);
            }
        }), 0);
        return cw.toByteArray();
    }
}
