package dev.glitg.core.domain;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DamagePolicyTest {
    @Test void capsHealthPointsAndLeavesUnknownSourcesUntouched(){var policy=new DamagePolicy(Map.of("mace",20.0));assertEquals(20.0,policy.cap("mace",60.0));assertEquals(8.0,policy.cap("projectile",8.0));}
    @Test void rejectsInvalidNumbers(){assertThrows(IllegalArgumentException.class,()->new DamagePolicy(Map.of("tnt",Double.NaN)));}
}
