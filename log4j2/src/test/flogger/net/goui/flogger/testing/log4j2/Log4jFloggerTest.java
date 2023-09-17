package net.goui.flogger.testing.log4j2;

import static com.google.common.flogger.LogContext.Key.TAGS;
import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.LevelClass.FINE;
import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.truth.LogSubject.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.context.Tags;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.SetLogLevel;
import net.goui.flogger.testing.api.FloggerBinding;
import net.goui.flogger.testing.api.LogInterceptor.Support;
import net.goui.flogger.testing.junit4.FloggerTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests which combine the Log4J interceptor with Flogger (FluentLogger).
 *
 * <p>While it's possible to use the testing API in Log4J without directly depending on Flogger,
 * there are many benefits to doing so, some of which are tested below.
 *
 * <p>This test exists in a separate source folder so the common tests can be run without a
 * dependency on Flogger.
 */
@RunWith(JUnit4.class)
public class Log4jFloggerTest {
  private static final FluentLogger flogger = FluentLogger.forEnclosingClass();

  @Rule public final FloggerTestRule logs = FloggerTestRule.forClass(Log4jFloggerTest.class, INFO);

  @Test
  public void testFactory_fullSupport() {
    assertThat(FloggerBinding.isFloggerAvailable()).isTrue();
    assertThat(new Log4jInterceptor.Factory().getSupportLevel()).isEqualTo(Support.FULL);
  }

  @Test
  public void testBasicLogging() {
    flogger.atWarning().withCause(new IllegalStateException("Oopsie!")).log("Warning: Badness");
    flogger.atInfo().with(TAGS, Tags.of("foo", "bar")).log("Hello World");
    flogger.atFine().log("Ignore me!");

    logs.assertLogs().matchCount().isEqualTo(2);
    logs.assertLog(0).hasMessageContaining("Badness");
    logs.assertLog(0).hasCause(IllegalStateException.class);
    logs.assertLog(1).hasMessageContaining("Hello");
    logs.assertLog(1).hasMetadata("foo", "bar");
  }

  // This demonstrates how built in rate limiting in Flogger "plays well" with the testing API.
  // The test rule doesn't change the log level (it's INFO by default anyway) but it does force
  // logging for all specified levels, so tests never need to worry about missing rate-limited
  // logs.
  @Test
  public void testForcedLogging() {
    for (int i = 0; i < 5; i++) {
      // Without forcing, this would emit at i={0,3} only; with forcing, everything is emitted.
      flogger.atInfo().every(3).log("Rate limited: index=%d", i);
    }
    logs.assertLogs().matchCount().isEqualTo(5);
    logs.assertLogs().always().haveMetadata("forced", true);
    logs.assertLogs().always().haveMessageContaining("Rate limited", "index");
  }

  @Test
  @SetLogLevel(target = Log4jFloggerTest.class, level = FINE)
  public void testSetLogLevel() {
    flogger.atInfo().log("Foo");
    flogger.atFine().log("Not Ignored");
    flogger.atFinest().log("Ignored");
    flogger.atWarning().log("Bar");

    logs.assertLogs().matchCount().isEqualTo(3);
    logs.assertLogs().withLevelLessThan(FINE).doNotOccur();
    LogEntry fine = logs.assertLogs().withLevel(FINE).getOnlyMatch();
    assertThat(fine).message().isEqualTo("Not Ignored");
  }
}
