package dev.glitg.core.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ItemTraversalAndLimitTest {
    private static ItemDescriptor item(String material,int amount){return new ItemDescriptor(material,null,null,Map.of(),Map.of(),Set.of(),amount);}
    @Test void traversesNestedStorageAndCountsLimits(){
        var roots=List.of(new ItemNode(item("shulker_box",1),List.of(new ItemNode(item("diamond",3),List.of(new ItemNode(item("diamond",2),List.of()))))));
        var flattened=new ItemTraversal(4,20).flatten(roots);
        assertEquals(3,flattened.size());
        var rule=new ItemRule("diamonds",true,Set.of(ItemAction.ALL),"diamond",null,null,Map.of(),Map.of(),Set.of());
        var decision=new ItemLimitCalculator(new ItemMatcher()).evaluate(rule,6,flattened,item("diamond",2));
        assertFalse(decision.allowed());assertEquals(1,decision.overflow());
    }
    @Test void depthAndNodeLimitsAreDeterministic(){
        var child=new ItemNode(item("diamond",1),List.of());var root=new ItemNode(item("bundle",1),List.of(child));
        assertEquals(1,new ItemTraversal(0,10).flatten(List.of(root)).size());
        assertThrows(IllegalStateException.class,()->new ItemTraversal(4,1).flatten(List.of(root)));
    }
    @Test void groupedVariantsShareOneMaximum(){
        var regular=new ItemRule("regular",true,Set.of(ItemAction.ALL),"potion","healing",null,Map.of(),Map.of(),Set.of());
        var splash=new ItemRule("splash",true,Set.of(ItemAction.ALL),"splash_potion","healing",null,Map.of(),Map.of(),Set.of());
        var inventory=List.of(new ItemDescriptor("potion","healing",null,Map.of(),Map.of(),Set.of(),4));
        var incoming=new ItemDescriptor("splash_potion","healing",null,Map.of(),Map.of(),Set.of(),3);
        var decision=new ItemLimitCalculator(new ItemMatcher()).evaluateGroup(List.of(regular,splash),6,inventory,incoming);
        assertFalse(decision.allowed());
        assertEquals(1,decision.overflow());
    }
}
