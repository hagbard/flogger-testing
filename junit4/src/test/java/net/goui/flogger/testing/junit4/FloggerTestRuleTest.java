package net.goui.flogger.testing.junit4;

import static net.goui.flogger.testing.core.LogEntry.LevelClass.FINE;
import static net.goui.flogger.testing.core.LogEntry.LevelClass.SEVERE;
import static net.goui.flogger.testing.core.LogEntry.LevelClass.WARNING;

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
  public FloggerTestRule logs =
      FloggerTestRule.forClass(FloggerTestRuleTest.class, Level.FINE)
          .asserting(
              logs -> logs.assertThat().everyLog().atOrAboveLevel(WARNING).messageContains("Warn"));

  @Test
  public void test() {
    logger.atWarning().withCause(new IllegalArgumentException("Oopsie!")).log("Warning: foo");
    logger.atInfo().log("Message: <long message that will be truncated for debugging>");
    logger.atFine().with(Key.TAGS, Tags.of("foo", 123)).log("Message: bar");

    // @formatter:off

    // --------------------------------------------------------------------------------------------
    // value of                   : log.level()
    // expected to be greater than: SEVERE
    // but was                    : WARNING
    // log was                    : Log{WARNING: 'Message: foo', cause=IllegalArgumentException,
    // context={forced=[true]}}
    // --------------------------------------------------------------------------------------------
    // logs.assertLog(0).levelIsAbove(SEVERE);

    // --------------------------------------------------------------------------------------------
    // value of: log.level()
    // expected: FINE
    // but was : INFO
    // log was : Log{INFO: 'Message: <long message that wi...', context={forced=[true]}}
    // --------------------------------------------------------------------------------------------
    // logs.assertLog(1).levelIs(FINE);

    // --------------------------------------------------------------------------------------------
    // value of                       : log.message()
    // expected to contain a match for: ot[A-Z]er
    // but was                        : Message: <long message that will be truncated for debugging>
    // log was                        : Log{INFO: 'Message: <long message that wi...',
    // context={forced=[true]}}
    // --------------------------------------------------------------------------------------------
    // logs.assertLog(1).messageMatches("ot[A-Z]er");

    // --------------------------------------------------------------------------------------------
    // log metadata did not contain entry {foo: 567}
    // value of           : log.metadata().get("foo")
    // expected to contain: 567
    // but was            : [123]
    // log was            : Log{FINE: 'Message: bar', context={forced=[true], foo=[123]}}
    // --------------------------------------------------------------------------------------------
    // logs.assertLog(2).metadataContains("foo", 567);

    // --------------------------------------------------------------------------------------------
    // value of            : log.cause()
    // expected instance of: java.lang.RuntimeException
    // but was             : null
    // log was             : Log{INFO: 'Message: <long message that wi...', context={forced=[true]}}
    // --------------------------------------------------------------------------------------------
    // logs.assertLog(1).hasCause(RuntimeException.class);

    // --------------------------------------------------------------------------------------------
    // value of                                       : logs.everyLog().atOrAboveLevel(WARNING)
    // expected every log message to contain substring: Woot!
    // but it was not true
    // failing logs                                   : [Log{WARNING: 'Message: foo',
    // cause=IllegalArgumentException, context={forced=[true]}}]
    // --------------------------------------------------------------------------------------------
    // logs.assertThat().everyLog().atOrAboveLevel(WARNING).messageContains("Woot!");

    // --------------------------------------------------------------------------------------------
    // value of                           : logs.noLog().atOrBelowLevel(WARNING)
    // expected no log cause to be of type: class java.lang.RuntimeException
    // but it was not true
    // failing logs                       : [Log{WARNING: 'Message: foo',
    // cause=IllegalArgumentException, context={forced=[true]}}]
    // --------------------------------------------------------------------------------------------
    // logs.assertThat().noLog().atOrBelowLevel(WARNING).hasCause(RuntimeException.class);

    // --------------------------------------------------------------------------------------------
    // value of                                            : logs.anyLog()
    // expected any log message to match regular expression: xxx|yyy
    // but it was not true
    // failing logs                                        : [Log{WARNING: 'Message: foo',
    // cause=IllegalArgumentException, context={forced=[true]}}, Log{INFO: 'Message: <long message
    // that wi...', context={forced=[true]}}, Log{FINE: 'Message: bar', context={forced=[true],
    // foo=[123]}}]
    // --------------------------------------------------------------------------------------------
    // logs.assertThat().anyLog().messageMatches("xxx|yyy");

    // --------------------------------------------------------------------------------------------
    // value of                            : logs.anyLog()
    // expected any log metadata to contain: foo=789
    // but it was not true
    // failing logs                        : [Log{WARNING: 'Message: foo',
    // cause=IllegalArgumentException, context={forced=[true]}}, Log{INFO: 'Message: <long message
    // that wi...', context={forced=[true]}}, Log{FINE: 'Message: bar', context={forced=[true],
    // foo=[123]}}]
    // --------------------------------------------------------------------------------------------
    // logs.assertThat().anyLog().metadataContains("foo", 789);

    // @formatter:on
  }
}
