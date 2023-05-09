package net.goui.flogger.testing.junit4;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static org.junit.Assert.assertTrue;

import com.google.common.flogger.FluentLogger;
import java.util.logging.Level;
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

    logs.assertLog(0).hasLevelAbove(INFO);

    logs.assertLog(1).hasLevelCompatibleWith(INFO);
    logs.assertLog(1).message().matches(".*other.*");

    logs.assertLog(2).hasLevelBelow(INFO);

    logs.assertLogs().atOrAbove(WARNING).allMessagesContain("foo");
    logs.assertLogs().noMessagesMatch(".*error.*");
    logs.assertLogs().someMessageMatches(".*(foo|bar).*").times(2);


//    logs.assertLogs().every().messageMatches(".*foo.*");
//
//    logs.assertLogs().atOrAbove(INFO).every().messageMatches(".*foo.*");
//    logs.assertLogs().atOrAbove(INFO).some().messageMatches(".*foo.*");
//    logs.assertLogs().atOrAbove(INFO).no().messageMatches(".*foo.*");
//    logs.assertLogs().atOrAbove(INFO).atLeast(2).messageMatches(".*foo.*");
//    logs.assertLogs().atOrAbove(INFO).times(2).messageMatches(".*foo.*");
  }
}
