package net.goui.flogger.testing.junit4;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LogContext.Key;
import com.google.common.flogger.context.Tags;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FloggerTestRuleTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule public FloggerTestRule logs = FloggerTestRule.forClass(FloggerTestRuleTest.class, FINE);

  @Test
  public void test() {
    logger.atWarning().withCause(new IllegalArgumentException("Oopsie!")).log("Message: foo");
    logger.atInfo().log("Message: <other>");
    logger.atFine().with(Key.TAGS, Tags.of("foo", 123)).log("Message: bar");

    logs.assertLog(0).levelIsAbove(INFO);

    logs.assertLog(1).levelIsCompatibleWith(INFO);
    logs.assertLog(1).messageMatches("ot[th]er");

    logs.assertLog(2).levelIsBelow(INFO);
    logs.assertLog(2).metadataContains("foo", 123);

    logs.assertLogs().everyLog().atOrAboveLevel(WARNING).messageContains("foo");
    logs.assertLog(0).hasCause(RuntimeException.class);
    logs.assertLogs().noLog().atOrBelowLevel(INFO).hasCause(RuntimeException.class);
    logs.assertLogs().anyLog().messageMatches("foo|bar");
    logs.assertLogs().anyLog().metadataContains("foo", 123);
  }
}
