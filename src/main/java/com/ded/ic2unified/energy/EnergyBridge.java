package com.ded.ic2unified.energy;

import com.ded.ic2unified.IC2Unified;
import com.ded.ic2unified.UnifiedConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * Keeps both energy nets in sync: every energy tile registered in one IC2 energy net gets a mirror (see {@link Mirror})
 * registered in the other one. Cables of IC2 Classic thus connect to machines of IC2 Experimental and vice versa.
 * <p>
 * Energy is never duplicated: a mirror forwards {@code getOfferedEnergy/drawEnergy/injectEnergy} to the real tile, so
 * whichever energy net moves the energy, it is taken from (and given to) the real buffers.
 * <p>
 * Registration is deferred to the start of the next server tick, so we never modify an energy net from inside its own
 * event handling.
 */
public final class EnergyBridge {

    public static final EnergyBridge INSTANCE = new EnergyBridge();

    private static final class Pending {
        final Api api;
        final Object tile;
        final boolean load;

        Pending(Api api, Object tile, boolean load) {
            this.api = api;
            this.tile = tile;
            this.load = load;
        }
    }

    private static final class Entry {
        final Api api; // api of the real tile
        final World world;
        final List<Object> mirrors = new ArrayList<>(1);

        Entry(Api api, World world) {
            this.api = api;
            this.world = world;
        }
    }

    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    /** real tile -> its mirrors in the other energy net */
    private final Map<Object, Entry> mirrored = new IdentityHashMap<>();
    private int errors;

    private EnergyBridge() {}

    // ------------------------------------------------------------------------------------------------------ events

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExpLoad(ic2.api.energy.event.EnergyTileLoadEvent event) {
        enqueue(Api.EXPERIMENTAL, event.tile, true, event.getWorld());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExpUnload(ic2.api.energy.event.EnergyTileUnloadEvent event) {
        enqueue(Api.EXPERIMENTAL, event.tile, false, event.getWorld());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClassicLoad(ic2classic.api.energy.event.EnergyTileLoadEvent event) {
        enqueue(Api.CLASSIC, event.tile, true, event.getWorld());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClassicUnload(ic2classic.api.energy.event.EnergyTileUnloadEvent event) {
        enqueue(Api.CLASSIC, event.tile, false, event.getWorld());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || pending.isEmpty()) return;
        Pending p;
        while ((p = pending.poll()) != null) {
            try {
                if (p.load) load(p.api, p.tile);
                else unload(p.tile);
            } catch (Throwable t) {
                if (errors++ < 20) IC2Unified.LOGGER.error("Failed to {} energy mirror for {}", p.load ? "add" : "remove", p.tile, t);
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World w = event.getWorld();
        if (w.isRemote) return;
        pending.removeIf(p -> safeWorld(p.api, p.tile) == w);
        mirrored.values().removeIf(e -> e.world == w); // the energy nets drop their whole world state themselves
    }

    private void enqueue(Api api, Object tile, boolean load, World world) {
        if (tile == null || tile instanceof Mirror.Marker || world == null || world.isRemote) return;
        if (load) {
            pending.add(new Pending(api, tile, true));
        } else {
            // unload right away: the real tile is gone from its own net, its mirror must not outlive it
            pending.removeIf(p -> p.tile == tile);
            try {
                unload(tile);
            } catch (Throwable t) {
                if (errors++ < 20) IC2Unified.LOGGER.error("Failed to remove energy mirror for {}", tile, t);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------ logic

    private void load(Api api, Object tile) {
        if (mirrored.containsKey(tile)) return;
        World world = safeWorld(api, tile);
        if (world == null || world.isRemote) return;

        Entry entry = new Entry(api, world);
        List<?> subs = api.subTiles(tile);
        if (subs != null && !subs.isEmpty()) {
            for (Object sub : subs) addMirror(entry, api, sub, world);
        } else {
            addMirror(entry, api, tile, world);
        }
        if (!entry.mirrors.isEmpty()) mirrored.put(tile, entry);
    }

    private void addMirror(Entry entry, Api api, Object tile, World world) {
        if (!UnifiedConfig.energy.mirrorConductors && api.isConductor(tile)) return;
        BlockPos pos = api.pos(tile);
        if (pos == null) return;
        // do not hide a real tile of the other mod at the same position (should never happen, one TE per block)
        Object foreignNet = api.other().net();
        if (foreignNet == null) return;
        Object mirror = Mirror.create(api, tile, world, pos);
        if (mirror == null) return;
        api.other().add(mirror);
        entry.mirrors.add(mirror);
    }

    private void unload(Object tile) {
        // the tile may still be queued for loading in the same tick (chunk loaded and unloaded immediately)
        Entry entry = mirrored.remove(tile);
        if (entry == null) return;
        for (Object mirror : entry.mirrors) {
            try {
                entry.api.other().remove(mirror);
            } catch (RuntimeException e) {
                if (errors++ < 20) IC2Unified.LOGGER.warn("Failed to remove energy mirror {}", mirror, e);
            }
        }
    }

    private static World safeWorld(Api api, Object tile) {
        try {
            return api.world(tile);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
