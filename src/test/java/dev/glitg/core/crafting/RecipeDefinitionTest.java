package dev.glitg.core.crafting;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RecipeDefinitionTest {
    @Test void validatesShapedRecipeSymbols(){assertDoesNotThrow(()->new RecipeDefinition("gap",true,RecipeDefinition.Type.SHAPED,"GOLDEN_APPLE",1,List.of(" G ","GAG"," G "),Map.of('G',"GOLD_INGOT",'A',"APPLE")));assertThrows(IllegalArgumentException.class,()->new RecipeDefinition("broken",true,RecipeDefinition.Type.SHAPED,"STONE",1,List.of("X"),Map.of()));}
}
