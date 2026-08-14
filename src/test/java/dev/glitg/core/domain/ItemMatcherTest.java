package dev.glitg.core.domain;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ItemMatcherTest {
    private final ItemMatcher matcher=new ItemMatcher();
    @Test void matchesEveryExplicitIdentityField(){
        var rule=new ItemRule("specific",true,Set.of(ItemAction.INTERACT),"minecraft:potion","minecraft:strong_strength",42,
                Map.of("glitgcore:id","power"),Map.of("minecraft:sharpness",3),Set.of("custom"));
        var item=new ItemDescriptor("MINECRAFT:POTION","minecraft:strong_strength",42,Map.of("glitgcore:id","power","other","kept"),
                Map.of("minecraft:sharpness",5),Set.of("custom","other"),1);
        assertTrue(matcher.matches(rule,item));
        assertTrue(rule.appliesTo(ItemAction.INTERACT));
        assertFalse(rule.appliesTo(ItemAction.CRAFT));
    }
    @Test void neverMatchesOnlyByDisplayName(){
        var rule=new ItemRule("vanilla",true,Set.of(ItemAction.ALL),"minecraft:diamond",null,null,Map.of(),Map.of(),Set.of());
        assertFalse(matcher.matches(rule,new ItemDescriptor("minecraft:paper",null,null,Map.of(),Map.of(),Set.of("name:diamond"),1)));
    }
    @Test void distinguishesPotionIdentity(){
        var rule=new ItemRule("strength",true,Set.of(ItemAction.ALL),"minecraft:splash_potion","minecraft:strong_strength",null,Map.of(),Map.of(),Set.of());
        assertFalse(matcher.matches(rule,new ItemDescriptor("minecraft:splash_potion","minecraft:long_strength",null,Map.of(),Map.of(),Set.of(),1)));
    }
}
