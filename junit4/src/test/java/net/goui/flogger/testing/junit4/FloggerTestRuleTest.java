package net.goui.flogger.testing.junit4;

import static net.goui.flogger.testing.core.LogEntry.LevelClass.SEVERE;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LogContext.Key;
import com.google.common.flogger.context.Tags;
import java.util.logging.Level;
import net.goui.flogger.testing.core.LogEntry.LevelClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FloggerTestRuleTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule
  public FloggerTestRule logs = FloggerTestRule.forClass(FloggerTestRuleTest.class, Level.FINE);

  @Test
  public void test() {
    logger.atWarning().withCause(new IllegalArgumentException("Oopsie!")).log("Message: foo");
    logger.atInfo().log("Message: <other>");
    logger.atFine().with(Key.TAGS, Tags.of("foo", 123)).log("Message: bar");

    // @formatter:off

    // --------------------------------------------------------------------------------------------
    // value of                    : logs.get(0)
    // expected to have level above: SEVERE
    // but was                     : Log{WARNING: 'Message: foo', cause=IllegalArgumentException, context={forced=[true]}}
    // --------------------------------------------------------------------------------------------
    logs.assertLog(0).levelIsAbove(SEVERE);

    // --------------------------------------------------------------------------------------------
    // value of                                          : log.log(1)
    // expected logged level 'INFO' to be compatible with: FINE
    // --------------------------------------------------------------------------------------------
    // logs.assertLog(1).levelIsCompatibleWith(FINE);

    // --------------------------------------------------------------------------------------------
    // value of                                        : log.log(1)
    // expected log message to match regular expression: ot[A-Z]er
    // but was                                         : Log{INFO: message='Message: <other>',
    // cause='null', metadata='{forced=[true]}'}
    // --------------------------------------------------------------------------------------------
    // logs.assertLog(1).messageMatches("ot[A-Z]er");

    // --------------------------------------------------------------------------------------------
    // expected metadata to contain 'foo'='567'
    // value of           : log.log(2).metadata[foo]
    // expected to contain: 567
    // but was            : [123]
    // --------------------------------------------------------------------------------------------
    // logs.assertLog(2).metadataContains("foo", 567);

    // --------------------------------------------------------------------------------------------
    // value of                                          : log.everyLog().atOrAboveLevel(WARNING)
    // expected message of every log to contain substring: Woot!
    // but some did not
    // first few unexpected logs                         : ...
    // --------------------------------------------------------------------------------------------
    // logs.assertLogs().everyLog().atOrAboveLevel(WARNING).messageContains("Woot!");

    // --------------------------------------------------------------------------------------------
    // expected cause to be of type: class java.lang.RuntimeException
    // value of            : log.log(1).cause()
    // expected instance of: java.lang.RuntimeException
    // but was             : null
    // --------------------------------------------------------------------------------------------
    // logs.assertLog(1).hasCause(RuntimeException.class);

    // --------------------------------------------------------------------------------------------
    // value of                              : log.noLog().atOrBelowLevel(WARNING)
    // expected cause of no log to be of type: class java.lang.RuntimeException
    // but some did
    // first few unexpected logs             : ...
    // --------------------------------------------------------------------------------------------
    // logs.assertLogs().noLog().atOrBelowLevel(WARNING).hasCause(RuntimeException.class);

    // --------------------------------------------------------------------------------------------
    // value of                                               : log.anyLog()
    // expected message of any log to match regular expression: xxx|yyy
    // but none did
    // first few logs                                         : ...
    // --------------------------------------------------------------------------------------------
    // logs.assertLogs().anyLog().messageMatches("xxx|yyy");

    // --------------------------------------------------------------------------------------------
    // value of                               : log.anyLog()
    // expected metadata of any log to contain: foo=789
    // but none did
    // first few logs                         : ...
    // --------------------------------------------------------------------------------------------
    // logs.assertLogs().anyLog().metadataContains("foo", 789);

    // @formatter:on
  }
}
