package net.goui.flogger.testing.junit4;

import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.LevelClass.WARNING;
import static net.goui.flogger.testing.truth.LogSubject.assertThat;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LogContext.Key;
import com.google.common.flogger.context.Tags;
import java.util.logging.Level;
import net.goui.flogger.testing.LogEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FloggerTestRuleTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule
  public final FloggerTestRule logs =
      FloggerTestRule.forClass(FloggerTestRuleTest.class, Level.FINE);




  @Test
  public void test() {
    logs.verify(logs -> logs.atOrAboveLevel(WARNING).always().haveMessageContaining("Warn"));








    logger.atWarning().withCause(new IllegalArgumentException("Oopsie!")).log("Warning: foo");
    logger.atInfo().log("Message: <long message that will be truncated for debugging>");
    logger.atFine().with(Key.TAGS, Tags.of("foo", 123)).log("Message: bar");

    // --------------------------------

    LogEntry warn = logs.assertLogs().withMessageContaining("foo").atLevel(WARNING).getOnlyMatch();
    logs.assertLogs().afterLog(warn).withMessageMatching("[Mm]es+age").atLevel(INFO).matchCount().isEqualTo(1);
    assertThat(warn).hasCause(IllegalArgumentException.class);

    LogEntry fine = logs.assertLogs().afterLog(warn).withMessageContaining("bar").getOnlyMatch();
    assertThat(fine).hasMetadata("foo", 123);

    logs.assertLogs().atLevel(WARNING).always().haveMessageMatching("foo");

    logs.assertLog(0).isAtLevel(WARNING);
    logs.assertLog(0).hasCause(RuntimeException.class);

    logs.assertLog(1).isAtLevel(INFO);
    logs.assertLog(1).containsMatch("[Mm]es+age");

    logs.assertLog(2).hasMetadata("foo", 123);

    logs.assertLogs().atOrAboveLevel(WARNING).always().haveMessageContaining("Warning");
    logs.assertLogs().belowLevel(WARNING).never().haveCause(RuntimeException.class);
  }
}
