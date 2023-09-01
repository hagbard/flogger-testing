package net.goui.flogger.testing.junit4;

import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.LevelClass.WARNING;
import static net.goui.flogger.testing.truth.LogSubject.assertThat;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LogContext.Key;
import com.google.common.flogger.context.Tags;

import java.util.logging.Level;

import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.truth.LogSubject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FloggerTestRuleTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule
  public FloggerTestRule logs =
      FloggerTestRule.forClass(FloggerTestRuleTest.class, Level.FINE)
          .asserting(logs -> logs.assertThat().everyLog().atOrAboveLevel(WARNING).contains("Warn"));

  @Test
  public void test() {
    logger.atWarning().withCause(new IllegalArgumentException("Oopsie!")).log("Warning: foo");
    logger.atInfo().log("Message: <long message that will be truncated for debugging>");
    logger.atFine().with(Key.TAGS, Tags.of("foo", 123)).log("Message: bar");

    LogEntry warn = logs.assertLogs().withMessageContaining("foo").atLevel(WARNING).get();
    logs.assertLogs().afterLog(warn).withMessageMatching("[Mm]es+age").atLevel(INFO).count().isEqualTo(1);
    LogEntry fine = logs.assertLogs().afterLog(warn).withMessageContaining("bar").get();
    assertThat(warn).hasCause(IllegalArgumentException.class);
    assertThat(fine).hasMetadata("foo", 123);
    logs.assertLogs().atLevel(WARNING).always().haveMessageMatching("foo");

    logs.assertLog(0).isAtLevel(WARNING);
    logs.assertLog(0).hasCause(RuntimeException.class);

    logs.assertLog(1).isAtLevel(INFO);
    logs.assertLog(1).containsMatch("[Mm]es+age");

    logs.assertLog(2).hasMetadata("foo", 123);

    logs.assertThat().everyLog().atOrAboveLevel(WARNING).contains("Warning");
    logs.assertThat().noLog().belowLevel(WARNING).hasCause(RuntimeException.class);

    logs.assertThat().anyLog().containsMatch("foo|bar");
    logs.assertThat().anyLog().belowLevel(INFO).hasMetadata("foo", 123);

//    try (Verifier warning = logs.verifyEveryLog().atLevel(WARNING)) {
//      warning.contains("");
//      warning.contains("");
//      warning.contains("");
//      warning.contains("");
//    }
//
// Log foo = logs.assertLog("...");
// Log bar = logs.assertLog().following(foo).atLevel().containing("bar");
// assertThat(foo).containsMetadata(...);
// assertThat(bar).containsMetadata(...);
//
// ---- post test ----
// logs.verify().noLog().atOrAboveLevel(WARNING);


//    logs.verifyOnly();
//    logs.expectLog().atLevel(WARNING).inMethod(clazz, "<method>").contains("foo");
//    logs.expectLog().atLevel(WARNING).inMethod(clazz, "<method>").contains("foo");
//    logs.verifyAllExpected("<class>");
//    logs.verifyAllExpected("<class>");
//    try (Logs toVerify = logs.inClass()) {}
  }
}
