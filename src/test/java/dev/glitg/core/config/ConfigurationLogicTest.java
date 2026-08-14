package dev.glitg.core.config;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationLogicTest {
    @Test void parsesDocumentedDurationUnits(){assertEquals(Duration.ofSeconds(5),DurationParser.parse("5s"));assertEquals(Duration.ofMinutes(2),DurationParser.parse("2m"));assertEquals(Duration.ofMillis(250),DurationParser.parse("250ms"));assertThrows(IllegalArgumentException.class,()->DurationParser.parse("soon"));}
    @Test void migrationRenamesLegacyCombatTimeAndPreservesUnknowns(){Map<String,Object> migrated=new ConfigMigration().migrate(Map.of("rules.combat_time",20,"custom.keep",true));assertEquals(2,migrated.get("config-version"));assertEquals(20,migrated.get("combat.duration-seconds"));assertEquals(false,migrated.get("features.operator-bypass"));assertEquals(true,migrated.get("custom.keep"));}
    @Test void validationReportsAllMalformedDurations(){var errors=new ConfigValidator().validate(Map.of("config-version",1,"combat.duration-seconds",-1,"grace.duration-seconds","bad"));assertEquals(2,errors.size());}
}
