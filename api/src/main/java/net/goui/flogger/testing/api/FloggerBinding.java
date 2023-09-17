/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.api;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Comparators.min;
import static java.lang.Boolean.TRUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static net.goui.flogger.testing.LevelClass.FINE;
import static net.goui.flogger.testing.LevelClass.FINEST;
import static net.goui.flogger.testing.LevelClass.INFO;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.backend.system.AbstractLogRecord;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopedLoggingContexts;
import com.google.common.flogger.context.Tags;
import java.io.Closeable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.LogRecord;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.LogInterceptor.Support;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Encapsulation of all Flogger library dependencies guarded behind a runtime class loading check.
 * This ensures that even without Flogger installed, users can get some value out of the testing
 * API.
 */
public final class FloggerBinding {
  // Tag label for a unique ID set for tests to support parallel testing with Flogger.
  private static final String TEST_ID = "test_id";
  private static final String EXPECTED_LOGGER_NAME = FloggerBinding.class.getName();

  @Nullable private static final FloggerBinding INSTANCE = determineInstance();

  /** Returns whether Flogger is available at runtime. */
  public static boolean isFloggerAvailable() {
    return INSTANCE != null;
  }

  /**
   * Installs a Flogger context if Flogger is available. When using the library without Flogger,
   * tests will not have "forced" logs or unique test IDs injected.
   */
  public static Closeable maybeInstallFloggerContext(
      Map<String, LevelClass> levelMap, String testId) {
    return INSTANCE != null ? INSTANCE.installFloggerContext(levelMap, testId) : null;
  }

  /**
   * Returns the best available timestamps from a JDK {@link LogRecord}, using Flogger APIs if
   * available for a better result.
   */
  public static Instant getBestTimestamp(LogRecord record) {
    return INSTANCE != null
        ? INSTANCE.getFloggerTimestamp(record)
        : getJdkLogRecordTimestamp(record);
  }

  /**
   * Returns the support level for the given factory. Used to determine the best available
   * interceptor.
   */
  public static Support getSupportLevel(AbstractLogInterceptorFactory factory) {
    checkState(
        INSTANCE != null, "Flogger must be available to determine interceptor support level");
    return INSTANCE.testSupportLevel(factory);
  }

  private FloggerBinding() throws ClassNotFoundException {
    Class.forName("com.google.common.flogger.FluentLogger");
  }

  Support testSupportLevel(AbstractLogInterceptorFactory factory) {
    factory.configureUnderlyingLoggerForInfoLogging(EXPECTED_LOGGER_NAME);
    LogInterceptor interceptor = factory.get();
    RuntimeException testCause = new RuntimeException();
    List<LogEntry> logged = new ArrayList<>();
    try (LogInterceptor.Recorder r =
        interceptor.attachTo(EXPECTED_LOGGER_NAME, FINE, logged::add, "DUMMY_TEST_ID")) {
      Lazy.logger.atInfo().withCause(testCause).log("<<enabled message>>");
      Lazy.logger.atFine().log("<<forced message>>");
      Lazy.logger.atFinest().log("<<disabled log>>");
    }
    if (logged.isEmpty()) {
      return Support.NONE;
    }
    // Support can only go down as checks fail.
    Support support = Support.FULL;
    LogEntry enabledLog = logged.get(0);
    support = min(support, testBasicSupport(enabledLog, INFO, "<<enabled message>>", testCause));
    if (logged.size() == 2) {
      LogEntry forcedLog = logged.get(1);
      support = min(support, testBasicSupport(forcedLog, FINEST, "<<forced message>>", null));
      // As well as basic support, test for the expected "forced=true" metadata.
      ImmutableList<Object> values = forcedLog.metadata().get("forced");
      if (values == null || !values.contains(TRUE)) {
        support = min(support, Support.PARTIAL);
      }
    } else {
      support = min(support, Support.PARTIAL);
    }
    return support;
  }

  private Closeable installFloggerContext(Map<String, LevelClass> levelMap, String testId) {
    // Skip adding test tags if the given ID is empty.
    Tags testTag = !testId.isEmpty() ? Tags.of(TEST_ID, testId) : Tags.empty();
    return ScopedLoggingContexts.newContext()
        .withLogLevelMap(
            LogLevelMap.create(Maps.transformValues(levelMap, LevelClass::toJdkLogLevel)))
        .withTags(testTag)
        .install();
  }

  private Instant getFloggerTimestamp(LogRecord record) {
    if (record instanceof AbstractLogRecord) {
      long timestampNanos = ((AbstractLogRecord) record).getLogData().getTimestampNanos();
      long seconds = NANOSECONDS.toSeconds(timestampNanos);
      return Instant.ofEpochSecond(seconds, timestampNanos - SECONDS.toNanos(seconds));
    } else {
      return getJdkLogRecordTimestamp(record);
    }
  }

  private static Instant getJdkLogRecordTimestamp(LogRecord record) {
    long timestampMillis = record.getMillis();
    long seconds = MILLISECONDS.toSeconds(timestampMillis);
    long millis = timestampMillis - SECONDS.toMillis(seconds);
    return Instant.ofEpochSecond(seconds, MILLISECONDS.toNanos(millis));
  }

  private static Support testBasicSupport(
      LogEntry e, LevelClass levelClass, String messageSubstring, @Nullable Throwable cause) {
    if (!e.message().contains(messageSubstring)) {
      return Support.NONE;
    }
    boolean fullSupport =
        e.className().equals(EXPECTED_LOGGER_NAME)
            && e.methodName().equals("getSupportLevel")
            && e.levelClass() == levelClass
            && Objects.equals(e.cause(), cause);
    return fullSupport ? Support.FULL : Support.PARTIAL;
  }

  private static FloggerBinding determineInstance() {
    try {
      return new FloggerBinding();
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private static final class Lazy {
    static final FluentLogger logger = FluentLogger.forEnclosingClass();
  }
}
