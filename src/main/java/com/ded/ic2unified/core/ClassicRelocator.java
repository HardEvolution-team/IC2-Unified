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
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Rewrites an IC2 Classic jar so that it can live next to IC2 Experimental:
 * <ul>
 *     <li>every class in {@code ic2/**} is moved to {@code ic2classic/**} (including its private copy of the IC2 API);</li>
 *     <li>mod id {@code ic2} becomes {@code ic2classic} (annotations, ResourceLocations, network channels, registry names...);</li>
 *     <li>{@code assets/ic2} becomes {@code assets/ic2classic}, {@code ic2:} references in json/lang/mcmeta are rewritten;</li>
 *     <li>translation keys that IC2 Experimental already defines are dropped from Classic's lang files, so Classic does not
 *     override Experimental's texts (a {@code ic2classic.*} copy of every {@code ic2.*} key is kept for Classic itself);</li>
 *     <li>FML annotation caches and jar signatures are removed (they would point to the old names).</li>
 * </ul>
 * Usage: {@code ClassicRelocator <IC2Classic.jar> <output.jar> [IC2Experimental.jar]}
 */
public final class ClassicRelocator {

    static final String OLD_ID = "ic2";
    static final String NEW_ID = "ic2classic";

    private static final Pattern RES_DOMAIN = Pattern.compile("(?<![A-Za-z0-9_.\\-])ic2:");
    /** Forge fluid names - both mods register fluids such as "ic2coolant", the FluidRegistry keeps only the first one. */
    private static final Pattern FLUID = Pattern.compile("(?<![A-Za-z0-9_])ic2(air|biogas|biomass|construction_foam|coolant|distilled_water|heavy_water"
            + "|hot_coolant|hot_water|hydrogen|oxygen|pahoehoe_lava|steam|superheated_steam|uu_matter|weed_ex)(?![A-Za-z0-9_])");
    /** FML @API names, must be unique between the two IC2 builds. */
    private static final Map<String, String> EXACT = Map.of("ic2api", "ic2classicapi", "ic2audioapi", "ic2classicaudioapi");

    private static final Pattern CLASS_NAME = Pattern.compile("ic2(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    private final Set<String> classes = new HashSet<>();   // internal names, e.g. ic2/core/IC2
    private final Set<String> packages = new HashSet<>();  // internal names, e.g. ic2/core
    private final Map<String, Set<String>> expLangKeys = new HashMap<>(); // lang file name (lower case) -> keys
    private final Set<String> leftovers = new TreeSet<>();

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: ClassicRelocator <IC2Classic.jar> <output.jar> [IC2Experimental.jar]");
            System.exit(1);
        }
        new ClassicRelocator().run(Paths.get(args[0]), Paths.get(args[1]), args.length > 2 ? Paths.get(args[2]) : null);
    }

    /** Relocates {@code in} (original IC2 Classic) into {@code out}; {@code exp} (IC2 Experimental jar) may be null. @return relocated class count */
    public static int relocate(Path in, Path out, Path exp) throws IOException {
        ClassicRelocator r = new ClassicRelocator();
        r.run(in, out, exp);
        return r.classes.size();
    }

    private void run(Path in, Path out, Path exp) throws IOException {
        if (exp != null) loadExpLang(exp);
        try (JarFile jar = new JarFile(in.toFile())) {
            for (JarEntry e : Collections.list(jar.entries())) {
                String n = e.getName();
                if (n.endsWith(".class") && n.startsWith(OLD_ID + "/")) {
                    String internal = n.substring(0, n.length() - 6);
                    classes.add(internal);
                    for (int i = internal.lastIndexOf('/'); i > 0; i = internal.lastIndexOf('/', i - 1)) {
                        packages.add(internal.substring(0, i));
                    }
                }
            }
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Manifest mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            mf.getMainAttributes().putValue("IC2Unified-Relocated", "true");
            Set<String> written = new HashSet<>();
            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(out), mf)) {
                written.add("META-INF/MANIFEST.MF");
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
                    String newName = mapPath(n);
                    if (n.endsWith(".class")) {
                        data = transformClass(data);
                    } else if (isText(n)) {
                        String text = new String(data, StandardCharsets.UTF_8);
                        text = RES_DOMAIN.matcher(text).replaceAll(NEW_ID + ":");
                        text = FLUID.matcher(text).replaceAll(NEW_ID + "_$1");
                        if (n.contains("/lang/")) text = transformLang(n, text);
                        data = text.getBytes(StandardCharsets.UTF_8);
                    }
                    if (!written.add(newName)) continue;
                    jos.putNextEntry(new ZipEntry(newName));
                    jos.write(data);
                    jos.closeEntry();
                }
            }
        }
        System.out.println("[ic2unified] Relocated " + classes.size() + " classes -> " + out);
        if (!leftovers.isEmpty()) {
            if (Boolean.getBoolean("ic2unified.debug")) System.out.println("[ic2unified] String constants starting with 'ic2' that were left untouched (" + leftovers.size() + "):");
            if (Boolean.getBoolean("ic2unified.debug")) for (String s : leftovers) System.out.println("    " + s);
        }
    }

    // ------------------------------------------------------------------ paths & strings

    private String mapPath(String n) {
        if (n.startsWith("assets/" + OLD_ID + "/")) return "assets/" + NEW_ID + n.substring(("assets/" + OLD_ID).length());
        if (n.startsWith(OLD_ID + "/")) return NEW_ID + n.substring(OLD_ID.length());
        return n;
    }

    private static boolean isText(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.endsWith(".json") || l.endsWith(".lang") || l.endsWith(".mcmeta") || l.endsWith(".info")
                || l.endsWith(".txt") || l.endsWith(".cfg") || l.endsWith(".ini") || l.endsWith(".properties");
    }

    String mapInternal(String internal) {
        if (internal == null) return null;
        if (internal.equals(OLD_ID) || internal.startsWith(OLD_ID + "/")) {
            String base = internal;
            int dollar = base.indexOf('$');
            String outer = dollar >= 0 ? base.substring(0, dollar) : base;
            if (classes.contains(outer) || classes.contains(base) || packages.contains(base) || base.equals(OLD_ID)) {
                return NEW_ID + internal.substring(OLD_ID.length());
            }
        }
        return internal;
    }

    String mapString(String s) {
        if (s.isEmpty() || !s.contains(OLD_ID)) return s;
        if (s.equals(OLD_ID)) return NEW_ID;
        if (EXACT.containsKey(s)) return EXACT.get(s);
        Matcher fluid = FLUID.matcher(s);
        if (fluid.find()) return fluid.replaceAll(NEW_ID + "_$1");
        if (s.startsWith(OLD_ID + ":")) return NEW_ID + s.substring(OLD_ID.length());
        if (s.startsWith("/assets/" + OLD_ID + "/") || s.startsWith("assets/" + OLD_ID + "/")) {
            return s.replaceFirst("assets/" + OLD_ID + "/", "assets/" + NEW_ID + "/");
        }
        // fully qualified class name or package, e.g. "ic2.core.platform.ClientProxy"
        if (CLASS_NAME.matcher(s).matches()) {
            String internal = s.replace('.', '/');
            String mapped = mapInternal(internal);
            if (!mapped.equals(internal)) return mapped.replace('/', '.');
        }
        // internal name or path, e.g. "ic2/core/..." or "Lic2/core/IC2;"
        if (s.startsWith(OLD_ID + "/")) {
            String mapped = mapInternal(s);
            if (!mapped.equals(s)) return mapped;
        }
        if (s.startsWith("L" + OLD_ID + "/") && s.endsWith(";")) {
            return "L" + mapInternal(s.substring(1, s.length() - 1)) + ";";
        }
        // embedded resource locations, e.g. "ic2:textures/..." inside larger strings
        Matcher m = RES_DOMAIN.matcher(s);
        if (m.find()) return m.replaceAll(NEW_ID + ":");
        if (s.startsWith(OLD_ID)) leftovers.add(s);
        return s;
    }

    // ------------------------------------------------------------------ classes

    private byte[] transformClass(byte[] data) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(0);
        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return mapInternal(internalName);
            }

            @Override
            public Object mapValue(Object value) {
                if (value instanceof String) return mapString((String) value);
                return super.mapValue(value);
            }
        };
        cr.accept(new ClassRemapper(cw, remapper), 0);
        return cw.toByteArray();
    }

    // ------------------------------------------------------------------ lang

    private void loadExpLang(Path exp) throws IOException {
        try (JarFile jar = new JarFile(exp.toFile())) {
            for (JarEntry e : Collections.list(jar.entries())) {
                String n = e.getName();
                if (n.startsWith("assets/" + OLD_ID + "/lang/") && n.endsWith(".lang")) {
                    Set<String> keys = new HashSet<>();
                    try (InputStream is = jar.getInputStream(e)) {
                        for (String line : new String(is.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n")) {
                            int eq = line.indexOf('=');
                            if (eq > 0 && !line.startsWith("#")) keys.add(line.substring(0, eq).trim());
                        }
                    }
                    expLangKeys.put(n.substring(n.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT), keys);
                }
            }
        }
    }

    private String transformLang(String name, String text) {
        Set<String> expKeys = expLangKeys.getOrDefault(name.substring(name.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT), Collections.emptySet());
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\r?\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0 || line.startsWith("#")) {
                sb.append(line).append('\n');
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1);
            String classicKey = key.replaceAll("(^|\\.)ic2\\.", "$1" + NEW_ID + ".");
            if (!classicKey.equals(key)) sb.append(classicKey).append('=').append(value).append('\n');
            if (!expKeys.contains(key)) sb.append(key).append('=').append(value).append('\n');
        }
        return sb.toString();
    }

}
