package com.ded.ic2unified.energy;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Describes one of the two (API-identical, but separately loaded) IC2 energy APIs:
 * {@code ic2.api.*} of IC2 Experimental and {@code ic2classic.api.*} of the relocated IC2 Classic.
 */
enum Api {
    EXPERIMENTAL("ic2.api.") {
        @Override
        Object net() {
            return ic2.api.energy.EnergyNet.instance;
        }

        @Override
        BlockPos pos(Object tile) {
            if (tile instanceof ic2.api.info.ILocatable) return ((ic2.api.info.ILocatable) tile).getPosition();
            if (tile instanceof TileEntity) return ((TileEntity) tile).getPos();
            return ic2.api.energy.EnergyNet.instance.getPos((ic2.api.energy.tile.IEnergyTile) tile);
        }

        @Override
        World world(Object tile) {
            if (tile instanceof ic2.api.info.ILocatable) return ((ic2.api.info.ILocatable) tile).getWorldObj();
            if (tile instanceof TileEntity) return ((TileEntity) tile).getWorld();
            return ic2.api.energy.EnergyNet.instance.getWorld((ic2.api.energy.tile.IEnergyTile) tile);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        void add(Object mirror) {
            ic2.api.energy.EnergyNet.instance.addTile((ic2.api.info.ILocatable & ic2.api.energy.tile.IEnergyTile) mirror);
        }

        @Override
        void remove(Object mirror) {
            ic2.api.energy.EnergyNet.instance.removeTile((ic2.api.energy.tile.IEnergyTile) mirror);
        }

        @Override
        List<?> subTiles(Object tile) {
            return tile instanceof ic2.api.energy.tile.IMetaDelegate ? ((ic2.api.energy.tile.IMetaDelegate) tile).getSubTiles() : null;
        }

        @Override
        boolean isEnergyTile(Object o) {
            return o instanceof ic2.api.energy.tile.IEnergyTile;
        }

        @Override
        boolean isConductor(Object o) {
            return o instanceof ic2.api.energy.tile.IEnergyConductor;
        }
    },

    CLASSIC("ic2classic.api.") {
        @Override
        Object net() {
            return ic2classic.api.energy.EnergyNet.instance;
        }

        @Override
        BlockPos pos(Object tile) {
            if (tile instanceof ic2classic.api.info.ILocatable) return ((ic2classic.api.info.ILocatable) tile).getPosition();
            if (tile instanceof TileEntity) return ((TileEntity) tile).getPos();
            return ic2classic.api.energy.EnergyNet.instance.getPos((ic2classic.api.energy.tile.IEnergyTile) tile);
        }

        @Override
        World world(Object tile) {
            if (tile instanceof ic2classic.api.info.ILocatable) return ((ic2classic.api.info.ILocatable) tile).getWorldObj();
            if (tile instanceof TileEntity) return ((TileEntity) tile).getWorld();
            return ic2classic.api.energy.EnergyNet.instance.getWorld((ic2classic.api.energy.tile.IEnergyTile) tile);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        void add(Object mirror) {
            ic2classic.api.energy.EnergyNet.instance.addTile((ic2classic.api.info.ILocatable & ic2classic.api.energy.tile.IEnergyTile) mirror);
        }

        @Override
        void remove(Object mirror) {
            ic2classic.api.energy.EnergyNet.instance.removeTile((ic2classic.api.energy.tile.IEnergyTile) mirror);
        }

        @Override
        List<?> subTiles(Object tile) {
            return tile instanceof ic2classic.api.energy.tile.IMetaDelegate ? ((ic2classic.api.energy.tile.IMetaDelegate) tile).getSubTiles() : null;
        }

        @Override
        boolean isEnergyTile(Object o) {
            return o instanceof ic2classic.api.energy.tile.IEnergyTile;
        }

        @Override
        boolean isConductor(Object o) {
            return o instanceof ic2classic.api.energy.tile.IEnergyConductor;
        }
    };

    /** Package prefix of the API, e.g. {@code ic2.api.} */
    final String prefix;

    Api(String prefix) {
        this.prefix = prefix;
    }

    Api other() {
        return this == EXPERIMENTAL ? CLASSIC : EXPERIMENTAL;
    }

    /** Name of the equivalent API class of {@code other()}, or null if {@code c} is not part of this API. */
    String translate(Class<?> c) {
        String n = c.getName();
        return n.startsWith(prefix) ? other().prefix + n.substring(prefix.length()) : null;
    }

    abstract Object net();

    abstract BlockPos pos(Object tile);

    abstract World world(Object tile);

    abstract void add(Object mirror);

    abstract void remove(Object mirror);

    abstract List<?> subTiles(Object tile);

    abstract boolean isEnergyTile(Object o);

    abstract boolean isConductor(Object o);

    Class<?> iLocatable() {
        return this == EXPERIMENTAL ? ic2.api.info.ILocatable.class : ic2classic.api.info.ILocatable.class;
    }

    Class<?> iMetaDelegate() {
        return this == EXPERIMENTAL ? ic2.api.energy.tile.IMetaDelegate.class : ic2classic.api.energy.tile.IMetaDelegate.class;
    }
}
