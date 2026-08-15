package dev.glitg.core.config;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationLogicTest {
    @Test void parsesDocumentedDurationUnits(){assertEquals(Duration.ofSeconds(5),DurationParser.parse("5s"));assertEquals(Duration.ofMinutes(2),DurationParser.parse("2m"));assertEquals(Duration.ofMillis(250),DurationParser.parse("250ms"));assertThrows(IllegalArgumentException.class,()->DurationParser.parse("soon"));}
    @Test void validationReportsAllMalformedDurations(){var errors=new ConfigValidator().validate(java.util.Map.of("config-version",1,"combat.duration-seconds",-1,"grace.duration-seconds","bad"));assertEquals(2,errors.size());}
    @Test void validationRejectsUnsupportedSchemas(){var errors=new ConfigValidator().validate(java.util.Map.of("config-version",2));assertEquals(java.util.List.of("config-version must be 1"),errors);}
    @Test void validationRejectsBadIndependentTimerAndDropMultiplier(){var errors=new ConfigValidator().validate(java.util.Map.of("config-version",1,"misc.hide-invisible-deaths-until","tomorrow","misc.breeze-rod-drop-multiplier",0));assertEquals(2,errors.size());}
}
