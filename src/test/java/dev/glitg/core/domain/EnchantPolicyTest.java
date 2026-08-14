package dev.glitg.core.domain;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EnchantPolicyTest {
    @Test void rejectsBannedAndAboveMaximumButHonorsExactExemption(){
        var policy=new EnchantPolicy(Set.of("minecraft:mending"),Map.of("minecraft:sharpness",5),Set.of("minecraft:custom_blade"));
        assertEquals(EnchantPolicy.Reason.BANNED,policy.validate(item("minecraft:sword",Map.of("minecraft:mending",1))).reason());
        assertEquals(5,policy.validate(item("minecraft:sword",Map.of("minecraft:sharpness",6))).maximumLevel());
        assertNull(policy.validate(item("minecraft:custom_blade",Map.of("minecraft:sharpness",10))));
    }
    private static ItemDescriptor item(String material,Map<String,Integer> enchants){return new ItemDescriptor(material,null,null,Map.of(),enchants,Set.of(),1);}
}
