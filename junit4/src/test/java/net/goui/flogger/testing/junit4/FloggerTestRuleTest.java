/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

This program and the accompanying materials are made available under the terms of the
Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.junit4;

import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.LevelClass.WARNING;
import static net.goui.flogger.testing.truth.LogSubject.assertThat;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LogContext.Key;
import com.google.common.flogger.context.Tags;
import java.util.logging.Level;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.truth.LogsSubject;
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

  private static void allWarningsHaveErrorMessage(LogsSubject assertLogs) {
    assertLogs.withLevelAtLeast(WARNING).always().haveMessageContaining("Error");
  }

  @Test
  public void test() {
    logs.verify(FloggerTestRuleTest::allWarningsHaveErrorMessage);

    logger.atWarning().log("Error: foo");
    logger.atInfo().log("Message: <short message>");
    logger.atWarning().withCause(new IllegalArgumentException("Oopsie!")).log("Error: bar");
    logger.atInfo().with(Key.TAGS, Tags.of("tag", 123)).log("Extra info: <short message>");
    logger
        .atFine()
        .with(Key.TAGS, Tags.of("tag", 123))
        .log("Extra info: <long message that will be truncated for debugging>");

    // --------------------------------

    LogEntry warn =
        logs.assertLogs().withLevel(WARNING).withMessageContaining("bar").getOnlyMatch();
    assertThat(warn).hasCause(IllegalArgumentException.class);
    logs.assertLogs()
        .afterLog(warn)
        .fromSameMethodAs(warn)
        .withMessageContaining("Extra info")
        .always()
        .haveMetadata("tag", 123);

    LogEntry fine = logs.assertLogs().afterLog(warn).withMessageContaining("bar").getOnlyMatch();
    assertThat(fine).hasMetadata("foo", 123);

    logs.assertLogs().withLevel(WARNING).always().haveMessageMatching("foo");

    logs.assertLog(0).hasLevel(WARNING);
    logs.assertLog(0).hasCause(RuntimeException.class);

    logs.assertLog(1).hasLevel(INFO);
    logs.assertLog(1).hasMessageMatching("[Mm]es+age");

    logs.assertLog(2).hasMetadata("foo", 123);

    logs.assertLogs().withLevelAtLeast(WARNING).always().haveMessageContaining("Warning");
    logs.assertLogs().withLevelLessThan(WARNING).never().haveCause(RuntimeException.class);
  }
}
