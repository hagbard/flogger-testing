/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.junit4;

import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.LevelClass.FINE;
import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.LevelClass.WARNING;
import static net.goui.flogger.testing.truth.LogMatcher.before;
import static net.goui.flogger.testing.truth.LogSubject.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.flogger.FluentLogger;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.SetLogLevel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;

/**
 * Unit tests for the JUnit4 integration of the logs testing API. Note that the core API itself is
 * well tested separately, so this test only needs to deal with integration with JUnit4.
 *
 * <p>The code within the body of these tests should be <em>identical</em> to the code in the JUnit5
 * tests, and only the test annotations should differ.
 */
@RunWith(JUnit4.class)
public class FloggerTestRuleTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Rule(order = 1)
  public ExpectTestFailureRule failureRule = new ExpectTestFailureRule();

  @Rule(order = 2)
  public final FloggerTestRule logs = FloggerTestRule.forClass(FloggerTestRuleTest.class, INFO);

  @BeforeClass
  public static void enableInfoLogs() {
    Logger.getLogger(FloggerTestRuleTest.class.getName()).setLevel(Level.INFO);
  }

  @Test
  public void testBasicApi() {
    logger.atInfo().log("Foo");
    logger.atFine().log("Ignored");
    logger.atInfo().log("Bar");
    logger.atWarning().withCause(new IllegalArgumentException()).log("Baz");

    LogEntry warn = logs.assertLogs().withLevel(WARNING).getMatch(0);
    assertThat(warn).hasMessageContaining("Baz");

    var logsBeforeWarn = logs.assertLogs(before(warn));
    // Match count is 2 because we are not logging at level FINE in this test.
    logsBeforeWarn.matchCount().isEqualTo(2);
    logsBeforeWarn.never().haveCause();
  }

  @Test
  @SetLogLevel(target = FloggerTestRuleTest.class, level = FINE)
  public void testExtraLogLevel() {
    logger.atInfo().log("Foo");
    logger.atFine().log("Not Ignored");
    logger.atFinest().log("Ignored");
    logger.atWarning().log("Bar");

    logs.assertLogs().matchCount().isEqualTo(3);
    logs.assertLogs().withLevelLessThan(FINE).doNotOccur();
    LogEntry fine = logs.assertLogs().withLevel(FINE).getOnlyMatch();
    assertThat(fine).message().isEqualTo("Not Ignored");
  }

  @Test
  public void testIndexedLogs() {
    logger.atInfo().log("Foo");
    logger.atWarning().log("Bar");

    logs.assertLogs().matchCount().isEqualTo(2);
    logs.assertLog(0).hasMessageContaining("Foo");
    logs.assertLog(1).hasMessageContaining("Bar");
    assertThrows(AssertionError.class, () -> logs.assertLog(0).hasMessageContaining("Bar"));
  }

  private static void call(Runnable logFn) {
    logFn.run();
  }

  private class Inner {
    void doLogging() {
      logger.atInfo().log("inner");
      call(() -> logger.atInfo().log("inner lambda"));
    }
  }

  private static class Nested {
    void doLogging() {
      logger.atInfo().log("nested");
      call(() -> logger.atInfo().log("nested lambda"));
    }
  }

  // Verifies that nested/inner classes and lambdas behave as expected.
  @Test
  public void testLogSiteInformation() {
    logger.atInfo().log("message");
    call(() -> logger.atInfo().log("lambda"));
    new Inner().doLogging();
    new Nested().doLogging();

    logs.assertLogs().matchCount().isEqualTo(6);

    logs.assertLog(0).hasMessageContaining("message");
    assertThat(logs.assertLogs().getMatch(0).className()).isEqualTo(getClass().getName());
    logs.assertLog(1).hasMessageContaining("lambda");
    assertThat(logs.assertLogs().getMatch(1).className()).isEqualTo(getClass().getName());

    logs.assertLog(2).hasMessageContaining("inner");
    assertThat(logs.assertLogs().getMatch(2).className()).isEqualTo(Inner.class.getName());
    logs.assertLog(3).hasMessageContaining("inner lambda");
    assertThat(logs.assertLogs().getMatch(3).className()).isEqualTo(Inner.class.getName());

    logs.assertLog(4).hasMessageContaining("nested");
    assertThat(logs.assertLogs().getMatch(4).className()).isEqualTo(Nested.class.getName());
    logs.assertLog(5).hasMessageContaining("nested lambda");
    assertThat(logs.assertLogs().getMatch(5).className()).isEqualTo(Nested.class.getName());
  }

  @Test
  @ExpectTestFailure
  public void testVerifyFailure() {
    try {
      // This should cause AssertionError *after* this method has finished.
      logs.verify(assertLogs -> assertLogs.always().haveMessageContaining("Foo"));
      logger.atInfo().log("Bar");
    } catch (AssertionError e) {
      throw new IllegalStateException("Test code should NOT fail!");
    }
  }

  // NOTE: We cannot use "@Test(expected = ...)" since that tests only the test method, not any
  // surrounding rules. So we make our own rule to catch expected failure in logging verification.

  @Retention(RetentionPolicy.RUNTIME)
  @Target(value = ElementType.METHOD)
  public @interface ExpectTestFailure {}

  public static class ExpectTestFailureRule implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          boolean expectedToFail = description.getAnnotation(ExpectTestFailure.class) != null;
          boolean failed = false;
          try {
            base.evaluate();
          } catch (AssertionError fail) {
            failed = true;
            if (!expectedToFail) throw fail;
          }
          if (expectedToFail && !failed) {
            Assert.fail("Test was expected to fail, but didn't");
          }
        }
      };
    }
  }
}
