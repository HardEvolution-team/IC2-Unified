package com.ded.ic2unified.energy;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A dynamic proxy that makes an energy tile of one IC2 API look like an energy tile of the other one.
 * <p>
 * Both APIs are binary identical apart from the package, so every call is forwarded to the method with the same name and
 * arity; energy tiles passed as arguments ({@code IEnergyEmitter}/{@code IEnergyAcceptor}) are unwrapped when they are
 * mirrors themselves, or wrapped into a light (unregistered) view otherwise.
 */
final class Mirror implements InvocationHandler {

    /** Marker implemented by every mirror proxy. */
    public interface Marker {
        Object ic2unified$target();
    }

    private static final Method TARGET_METHOD;
    private static final Map<Class<?>, Class<?>[]> INTERFACE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Method, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    /** Interfaces that are handled separately or have no meaning for a mirror. */
    private static final Set<String> SKIPPED = new HashSet<>(Arrays.asList(
            "energy.tile.IMetaDelegate", "info.ILocatable", "energy.tile.IEnergyTile"));

    static {
        try {
            TARGET_METHOD = Marker.class.getMethod("ic2unified$target");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** API the target belongs to. */
    final Api source;
    final Object target;
    final World world;
    final BlockPos pos;

    private Mirror(Api source, Object target, World world, BlockPos pos) {
        this.source = source;
        this.target = target;
        this.world = world;
        this.pos = pos;
    }

    /**
     * Creates a proxy that implements the {@code source.other()} equivalents of all energy interfaces of {@code target}.
     *
     * @return the proxy or null when the target implements no translatable interface
     */
    static Object create(Api source, Object target, World world, BlockPos pos) {
        Class<?>[] ifaces = INTERFACE_CACHE.computeIfAbsent(target.getClass(), c -> foreignInterfaces(source, c));
        if (ifaces.length == 0) return null;
        return Proxy.newProxyInstance(Mirror.class.getClassLoader(), ifaces, new Mirror(source, target, world, pos));
    }

    static Object unwrap(Object o) {
        return o instanceof Marker ? ((Marker) o).ic2unified$target() : o;
    }

    private static Class<?>[] foreignInterfaces(Api source, Class<?> type) {
        Api foreign = source.other();
        LinkedHashSet<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> i : allInterfaces(type)) {
            String rel = i.getName().startsWith(source.prefix) ? i.getName().substring(source.prefix.length()) : null;
            if (rel == null || SKIPPED.contains(rel) || !rel.startsWith("energy.tile.")) continue;
            try {
                result.add(Class.forName(foreign.prefix + rel, false, Mirror.class.getClassLoader()));
            } catch (ClassNotFoundException ignored) {
                // interface only exists in one of the APIs
            }
        }
        if (result.isEmpty()) return new Class<?>[0];
        result.add(foreign.iLocatable());
        result.add(Marker.class);
        return result.toArray(new Class<?>[0]);
    }

    private static Set<Class<?>> allInterfaces(Class<?> type) {
        Set<Class<?>> out = new LinkedHashSet<>();
        Deque<Class<?>> todo = new ArrayDeque<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) todo.addAll(Arrays.asList(c.getInterfaces()));
        while (!todo.isEmpty()) {
            Class<?> i = todo.poll();
            if (out.add(i)) todo.addAll(Arrays.asList(i.getInterfaces()));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------------------

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        switch (method.getName()) {
            case "equals":
                if (method.getParameterCount() == 1) return proxy == args[0];
                break;
            case "hashCode":
                if (method.getParameterCount() == 0) return System.identityHashCode(proxy);
                break;
            case "toString":
                if (method.getParameterCount() == 0) return "IC2UnifiedMirror[" + source.other() + " view of " + target + " @ " + pos + "]";
                break;
            case "getPosition":
                if (method.getParameterCount() == 0) return pos;
                break;
            case "getWorldObj":
                if (method.getParameterCount() == 0) return world;
                break;
            default:
                break;
        }
        if (method.equals(TARGET_METHOD)) return target;

        Method real = METHOD_CACHE.computeIfAbsent(method, m -> findTargetMethod(source, m));
        if (real == null || !real.getDeclaringClass().isInstance(target)) {
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            return defaultValue(method.getReturnType());
        }
        Object[] realArgs = args;
        if (args != null) {
            Class<?>[] types = real.getParameterTypes();
            realArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) realArgs[i] = convertArg(args[i], types[i]);
        }
        try {
            return real.invoke(target, realArgs);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** Converts an energy tile of the foreign API (that the energy net passed to us) into one of the target API. */
    private Object convertArg(Object arg, Class<?> expected) {
        if (arg == null || expected.isInstance(arg)) return arg;
        Object unwrapped = unwrap(arg);
        if (expected.isInstance(unwrapped)) return unwrapped;
        Api foreign = source.other();
        if (foreign.isEnergyTile(arg)) {
            // light view, never registered in any energy net
            Object view = create(foreign, arg, world, safePos(foreign, arg));
            if (view != null && expected.isInstance(view)) return view;
        }
        return arg; // will fail with a clear IllegalArgumentException
    }

    private BlockPos safePos(Api api, Object tile) {
        try {
            BlockPos p = api.pos(tile);
            return p != null ? p : pos;
        } catch (RuntimeException e) {
            return pos;
        }
    }

    private static Method findTargetMethod(Api source, Method foreignMethod) {
        String targetIfaceName = source.other().translate(foreignMethod.getDeclaringClass());
        if (targetIfaceName == null) return null;
        try {
            Class<?> iface = Class.forName(targetIfaceName, false, Mirror.class.getClassLoader());
            for (Method m : iface.getMethods()) {
                if (m.getName().equals(foreignMethod.getName()) && m.getParameterCount() == foreignMethod.getParameterCount()) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        return (char) 0;
    }
}
