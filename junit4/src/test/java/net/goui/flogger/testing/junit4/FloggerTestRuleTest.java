package net.goui.flogger.testing.junit4;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.junit.Assert.assertTrue;

import com.google.common.flogger.FluentLogger;
import net.goui.flogger.testing.core.truth.LogSubject;
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
    logger.atWarning().log("Message: foo");
    logger.atInfo().log("Message: <other>");
    logger.atFine().log("Message: bar");

    logs.assertLog(0).levelIsAbove(INFO);

    logs.assertLog(1).levelIsCompatibleWith(INFO);
    logs.assertLog(1).messageMatches("ot[th]er");

    logs.assertLog(2).levelIsBelow(INFO);

    logs.assertLogs().everyLog().atOrAboveLevel(WARNING).messageContains("foo");
    logs.assertLogs().noLog().messageContains("error");
    logs.assertLogs().anyLog().messageMatches("foo|bar");
  }
}
