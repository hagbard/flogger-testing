/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

This program and the accompanying materials are made available under the terms of the
Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.log4j2;

import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.truth.LogSubject.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;

import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.FloggerBinding;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.LogInterceptor.Recorder;
import net.goui.flogger.testing.junit4.FloggerTestRule;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Log4jInterceptorTest {
  private static final String TEST_ID = "Test ID";

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
  public void testIdFiltering() {
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
  public void testWithMdc() {
    Logger logger = logger("foo.bar.Baz");

    // MDC values set before the interceptor is added must be captured.
    ThreadContext.put("first", "value");

    LogInterceptor interceptor = Log4jInterceptor.create();
    List<LogEntry> logged = new ArrayList<>();
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", INFO, logged::add, TEST_ID)) {
      logger.info("With one MDC");

      // And values added between log statements must be captured.
      ThreadContext.put("second", "other");
      logger.info("With two MDC");

      assertThat(logged).hasSize(2);
      assertThat(logged.get(0)).hasMessageContaining("one", "MDC");
      assertThat(logged.get(0)).hasMetadata("first", "value");
      // Special case assertions go directly to the log entry.
      assertThat(logged.get(0).metadata()).hasSize(1);

      assertThat(logged.get(1)).hasMessageContaining("two", "MDC");
      assertThat(logged.get(1)).hasMetadata("first", "value");
      assertThat(logged.get(1)).hasMetadata("second", "other");
      assertThat(logged.get(1).metadata()).hasSize(2);
    }
  }
}
