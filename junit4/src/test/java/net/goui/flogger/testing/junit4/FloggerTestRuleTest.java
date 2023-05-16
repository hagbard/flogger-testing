package net.goui.flogger.testing.junit4;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LogContext.Key;
import com.google.common.flogger.context.Tags;
import java.util.logging.Level;
import net.goui.flogger.testing.LevelClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static net.goui.flogger.testing.LevelClass.*;

@RunWith(JUnit4.class)
public class FloggerTestRuleTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule
  public FloggerTestRule logs =
      FloggerTestRule.forClass(FloggerTestRuleTest.class, Level.FINE)
          .asserting(
              logs ->
                  logs.assertThat()
                      .everyLog()
                      .atOrAboveLevel(LevelClass.WARNING)
                      .contains("Warn"));

  @Test
  public void test() {
    logger.atWarning().withCause(new IllegalArgumentException("Oopsie!")).log("Warning: foo");
    logger.atInfo().log("Message: <long message that will be truncated for debugging>");
    logger.atFine().with(Key.TAGS, Tags.of("foo", 123)).log("Message: bar");

    // @formatter:off

    logs.assertLog(0).isAtLevel(WARNING);
    logs.assertLog(0).hasCause(RuntimeException.class);

    logs.assertLog(1).isAtLevel(INFO);
    logs.assertLog(1).containsMatch("[Mm]es+age");

    logs.assertLog(2).hasMetadata("foo", 123);

    logs.assertThat().everyLog().atOrAboveLevel(WARNING).contains("Warning");
    logs.assertThat().noLog().belowLevel(WARNING).hasCause(RuntimeException.class);

    logs.assertThat().anyLog().containsMatch("foo|bar");
    logs.assertThat().anyLog().belowLevel(INFO).hasMetadata("foo", 123);

    // @formatter:on
  }
}
