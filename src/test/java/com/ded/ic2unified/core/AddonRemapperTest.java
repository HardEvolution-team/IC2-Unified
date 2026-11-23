package com.ded.ic2unified.core;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AddonRemapperTest {
    private final AddonRemapper r = new AddonRemapper(Set.of("ic2/core/IC2", "ic2/api/classic/energy/IEnergy"));

    @Test
    void strings() {
        assertEquals("ic2classic", r.mapString("ic2"));
        assertEquals("required-after:ic2classic@[1.5,);after:jei", r.mapString("required-after:ic2@[1.5,);after:jei"));
        assertEquals("ic2classic:textures/x.png", r.mapString("ic2:textures/x.png"));
        assertEquals("ic2classic.core.IC2", r.mapString("ic2.core.IC2"));
        assertEquals("ic2classic.api.classic", r.mapString("ic2.api.classic"));
        assertEquals("ic2.message.hello", r.mapString("ic2.message.hello"));
        assertEquals("ic2classic_steam", r.mapString("ic2steam"));
        assertEquals("ic2classicapi", r.mapString("ic2api"));
        assertEquals("Lic2classic/core/IC2;", r.mapString("Lic2/core/IC2;"));
        assertEquals("myaddon:ic2_thing", r.mapString("myaddon:ic2_thing"));
    }
}
