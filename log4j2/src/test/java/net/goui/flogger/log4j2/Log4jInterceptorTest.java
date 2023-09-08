/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

 This program and the accompanying materials are made available under the terms of the
 Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

 SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.log4j2;

import static com.google.common.flogger.LogContext.Key.TAGS;
import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.LevelClass.FINE;
import static net.goui.flogger.testing.LevelClass.INFO;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.context.Tags;
import java.util.ArrayList;
import java.util.List;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.LogInterceptor.Recorder;
import net.goui.flogger.testing.api.LogInterceptor.Support;
import net.goui.flogger.testing.api.SetLogLevel;
import net.goui.flogger.testing.junit4.FloggerTestRule;
import net.goui.flogger.testing.log4j2.Log4jInterceptor;
import net.goui.flogger.testing.truth.LogSubject;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Log4jInterceptorTest {
  private static final String TEST_ID = "Test ID";

  private static final FluentLogger flogger = FluentLogger.forEnclosingClass();

  @Rule
  public final FloggerTestRule logs =
      FloggerTestRule.create(
          ImmutableMap.of(Log4jInterceptorTest.class.getName(), INFO), Log4jInterceptor.create());

  Logger logger(String name) {
    Logger logger = (Logger) LogManager.getLogger(name);
    // DO NOT use `logger.setLevel()` in tests because the state it sets is undone when the appender
    // is added. We assume that in testing users are not using loggers with transient state set.
    Configurator.setLevel(logger.getName(), Level.INFO);
    return logger;
  }

  @Test
  public void testFactory_fullSupport() {
    assertThat(new Log4jInterceptor.Factory().getSupportLevel()).isEqualTo(Support.FULL);
  }

  @Test
  public void testInterceptorScope_logNames() {
    Logger logger = logger("foo.bar.Baz");
    Logger childLogger = logger("foo.bar.Baz.Child");
    Logger parentLogger = logger("foo.bar");
    Logger siblingLogger = logger("foo.bar.Sibling");

    LogInterceptor interceptor = Log4jInterceptor.create();
    List<LogEntry> logged = new ArrayList<>();
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", INFO, logged::add, TEST_ID)) {

      assertThat(logged).isEmpty();

      logger.info("Log message");
      childLogger.info("Child message");
      parentLogger.info("Parent message");
      siblingLogger.info("Sibling message");

      assertThat(logged).hasSize(2);
      assertThat(logged.get(0).message()).isEqualTo("Log message");
      assertThat(logged.get(1).message()).isEqualTo("Child message");
    }
    logger.error("Should not be captured!!");
    assertThat(logged).hasSize(2);
  }

  @Test
  public void testInterceptorScope_logLevels() {
    Logger logger = logger("foo.bar.Baz");

    LogInterceptor interceptor = Log4jInterceptor.create();
    List<LogEntry> logged = new ArrayList<>();
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", INFO, logged::add, TEST_ID)) {
      logger.error("Message: Error");
      logger.warn("Message: Warn");
      logger.info("Message: Info");
      logger.debug("Message: Debug");
      logger.trace("Message: Trace");

      assertThat(logged).hasSize(3);
      assertThat(logged.get(0).message()).isEqualTo("Message: Error");
      assertThat(logged.get(1).message()).isEqualTo("Message: Warn");
      assertThat(logged.get(2).message()).isEqualTo("Message: Info");
    }
  }

  @Test
  public void testTestIdFiltering() {
    Logger logger = logger("foo.bar.Baz");

    LogInterceptor interceptor = Log4jInterceptor.create();
    List<LogEntry> logged = new ArrayList<>();
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", INFO, logged::add, TEST_ID)) {
      assertThat(logged).isEmpty();
      logger.info("No test ID");
      logger.info("Valid test ID [CONTEXT test_id=\"" + TEST_ID + "\" ]");
      logger.info("Multiple test IDs [CONTEXT test_id=\"" + TEST_ID + "\" test_id=\"xxx\" ]");
      logger.info("Unmatched test ID [CONTEXT test_id=\"xxx\" ]");

      assertThat(logged).hasSize(3);
      assertThat(logged.get(0).message()).isEqualTo("No test ID");
      assertThat(logged.get(1).message()).isEqualTo("Valid test ID");
      assertThat(logged.get(2).message()).isEqualTo("Multiple test IDs");
    }
  }

  @Test
  public void testWithFlogger() {
    flogger.atWarning().withCause(new IllegalStateException("Oopsie!")).log("Warning: Badness");
    flogger.atInfo().with(TAGS, Tags.of("foo", "bar")).log("Hello World");
    flogger.atFine().log("Ignore me!");

    logs.assertLogs().matchCount().isEqualTo(2);
    logs.assertLog(0).hasMessageContaining("Badness");
    logs.assertLog(0).hasCause(IllegalStateException.class);
    logs.assertLog(1).hasMessageContaining("Hello");
    logs.assertLog(1).hasMetadata("foo", "bar");
  }

  @Test
  @SetLogLevel(target = Log4jInterceptorTest.class, level = LevelClass.FINE)
  public void testSetLogLevel() {
    flogger.atInfo().log("Foo");
    flogger.atFine().log("Not Ignored");
    flogger.atFinest().log("Ignored");
    flogger.atWarning().log("Bar");

    logs.assertLogs().matchCount().isEqualTo(3);
    logs.assertLogs().withLevelLessThan(FINE).doNotOccur();
    LogEntry fine = logs.assertLogs().withLevel(FINE).getOnlyMatch();
    LogSubject.assertThat(fine).message().isEqualTo("Not Ignored");
  }
}
