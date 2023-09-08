/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

This program and the accompanying materials are made available under the terms of the
Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.junit5;

import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.LevelClass.FINE;
import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.LevelClass.WARNING;
import static net.goui.flogger.testing.junit5.FloggerTestExtension.guessClassUnderTest;
import static net.goui.flogger.testing.junit5.FloggerTestExtension.guessPackageUnderTest;
import static net.goui.flogger.testing.truth.LogMatcher.before;
import static net.goui.flogger.testing.truth.LogSubject.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.flogger.FluentLogger;
import com.google.common.truth.Truth;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.SetLogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Unit tests for the JUnit5 integration of the logs testing API. Note that the core API itself is
 * well tested separately, so this test only needs to deal with integration with JUnit5.
 *
 * <p>The code within the body of these tests should be <em>identical</em> to the code in the JUnit4
 * tests, and only the test annotations should differ.
 */
public class FloggerTestExtensionTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @RegisterExtension
  public final FloggerTestExtension logs =
      FloggerTestExtension.forClass(FloggerTestExtensionTest.class, INFO);

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
  @SetLogLevel(target = FloggerTestExtensionTest.class, level = FINE)
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

  @Test
  public void testGuessClassUnderTest() {
    // a.b.XxxTest.class --> "a.b.Xxx"
    assertThat(guessClassUnderTest(FloggerTestExtensionTest.class))
        .isEqualTo(FloggerTestExtension.class.getName());
    assertThrows(IllegalArgumentException.class, () -> guessClassUnderTest(String.class));
  }

  @Test
  public void testGuessPackageUnderTest() {
    // a.b.XxxTest.class --> "a.b"
    Truth.assertThat(guessPackageUnderTest(FloggerTestExtensionTest.class))
        .isEqualTo(getClass().getPackage().getName());
  }

  @Test
  public void testVerifyFailure() {
    logs.verify(assertLogs -> assertLogs.always().haveMessageContaining("Foo"));
    logger.atInfo().log("Bar");

    // A bit of a hack to call the lifecycle method manually (it relies on the logs extension not
    // caring if it's given null), but it seems to work fine.
    assertThrows(AssertionError.class, () -> logs.afterEach(null));
  }
}
