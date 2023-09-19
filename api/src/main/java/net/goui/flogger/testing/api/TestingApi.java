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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.StreamSubject.streams;
import static java.util.Arrays.asList;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static net.goui.flogger.testing.SetLogLevel.Scope.UNDEFINED;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.SetLogLevel;
import net.goui.flogger.testing.api.LogInterceptor.Recorder;
import net.goui.flogger.testing.truth.LogMatcher;
import net.goui.flogger.testing.truth.LogSubject;
import net.goui.flogger.testing.truth.LogsSubject;
import net.goui.flogger.testing.truth.MatchedLogsSubject;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Log testing API.
 *
 * <p>This API primarily uses two <a href="https://github.com/google/truth">Truth</a> "subjects" to
 * implement a rich testing API for logs. For specific details on Truth APIs, see {@link
 * LogsSubject} and {@link LogSubject}.
 *
 * <p>In this API there are three sub-APIs which share similarly named methods, but in each case
 * they have different roles.
 *
 * <ul>
 *   <li>Filtering logs via {@link LogsSubject}; {@code withMessageContaining()}, {@code
 *       withLevel()} ...
 *   <li>Asserting over all logs via {@link MatchedLogsSubject}; {@code haveMessageContaining()},
 *       {@code haveLevel()} ...
 *   <li>Asserting a single log via {@link LogSubject}; {@code hasMessageContaining()}, {@code
 *       hasLevel()} ...
 * </ul>
 *
 * <p>This naming convention helps distinguish the intent of log assertions such as:
 *
 * <pre>{@code
 * // Asserts that all warning logs contain "error".
 * logs.assertLogs().withLevel(WARNING).always().haveMessageContaining("error");
 * }</pre>
 *
 * versus (the somewhat less useful):
 *
 * <pre>{@code
 * // Asserts that all logs containing "error" are warning logs.
 * logs.assertLogs().withMessageContaining("error").always().haveLevel(WARNING);
 * }</pre>
 *
 * <pre>{@code
 * // Asserts that a single log entry is a warning log, and contains "error".
 * assertThat(logEntry).hasMessageContaining("error");
 * assertThat(logEntry).hasLevel(WARNING);
 * }</pre>
 *
 * <p>A subclass of this class will be installed per test case according to a specific test
 * framework (e.g. JUnit4 or JUnit5). One instance of this class is created and installed per test
 * case, before it is invoked. This means that in order to affect the state of the API during a
 * test, the instance must be mutable. It also means that things like the log-level map cannot be
 * modified after the instance is installed.
 */
@CheckReturnValue
public abstract class TestingApi<ApiT extends TestingApi<ApiT>> {
  // Tag label for a unique ID set for tests to support parallel testing with Flogger.
  @VisibleForTesting static final String TEST_ID = "test_id";

  private final ImmutableMap<String, LevelClass> defaultLevelMap;
  private LogInterceptor interceptor;
  // Captured logs (thread safe).
  private final ConcurrentLinkedQueue<LogEntry> logs = new ConcurrentLinkedQueue<>();
  private ImmutableList<LogEntry> logsSnapshot = ImmutableList.of();
  private Consumer<LogsSubject> verification;
  private final Set<LogEntry> excluded = new HashSet<>();

  protected TestingApi(Map<String, LevelClass> levelMap, @Nullable LogInterceptor interceptor) {
    this.defaultLevelMap = ImmutableMap.copyOf(levelMap);
    this.interceptor = interceptor;
    this.verification = s -> {};
  }

  /** Returns {@code this} instance from the concrete subclass for polymorphic resolution. */
  protected abstract ApiT api();

  private ImmutableList<LogEntry> logged() {
    if (logsSnapshot.size() != logs.size()) {
      // Copying the concurrent queue is thread safe.
      logsSnapshot = ImmutableList.copyOf(logs);
    }
    return logsSnapshot;
  }

  /**
   * Returns the expected logger name for a given class. Note that this is not as simple as
   * returning the class name, because nested and inner classes are still expected to use the logger
   * of the outer class. This is used by framework specific subclasses to determine the expected
   * logger for a class under test.
   */
  protected static String loggerNameOf(Class<?> clazz) {
    checkArgument(
        !clazz.isPrimitive(),
        "Invalid class for log capture (must not be an primitive): %s",
        clazz);
    checkArgument(
        !clazz.isArray(), "Invalid class for log capture (must not be an array): %s", clazz);
    String className = clazz.getCanonicalName();
    checkArgument(
        className != null,
        "Invalid class for log capture (does not have a canonical name): %s",
        clazz);
    return className.replace('$', '.');
  }

  /**
   * Begins a fluent assertion for a snapshot of the currently captured logs.
   *
   * <p>An assertion can be started with an optional list of matchers to restrict the set of log
   * entries on which assertions are made. This allows custom matchers and comparative matchers
   * (e.g. {@link LogMatcher#after(LogEntry)}) to be used in conjunction with the built in event
   * matcher methods (e.g. {@link LogsSubject#withMessageContaining(String,String...)}. For example:
   *
   * <pre>{@code
   * LogEntry debugStart = logs.assertLogs().withMessageContaining("Debug start").getOnlyMatch();
   * logs.assertLogs(after(debugStart))
   *     .withLevelAtLeast(WARNING)
   *     .always()
   *     .haveMetadataKey("debug_id");
   * }</pre>
   *
   * <p>Because {@code LogsSubject} and {@code LogEntry} instances are immutable, it is safe to
   * "split" a fluent assertion for readability. For example:
   *
   * <pre>{@code
   * // By naming the local variable 'assertXxx', the following assertions read more fluently.
   * var assertWarnings = logs.assertLogs().atLevel(WARNING);
   * // Any new warning logs made after this point are not going to affect 'assertWarnings'.
   * assertWarnings.matchCount().isGreaterThan(2);
   * assertWarnings.never().haveMetadata(REQUEST_ID, GOOD_TEST_ID);
   * assertWarnings.withMessageContaining("Read error").always().haveCause(IOException.class);
   * }</pre>
   */
  public LogsSubject assertLogs(LogMatcher... matchers) {
    return LogsSubject.assertThat(logged()).matching(matchers);
  }

  /**
   * Asserts that the given list of distinct log entries are in strict order in the currently
   * captured sequence of logs.
   *
   * <p>This method is useful when comparing the relative order of logs, and often avoids the need
   * to call {@link #assertLog(int)}, which can contribute to brittle logs testing.
   */
  public void assertLogOrder(LogEntry first, LogEntry second, LogEntry... rest) {
    ImmutableList<LogEntry> logged = logged();
    Truth.assertWithMessage("expected log entries to be in order")
        .about(streams())
        .that(Stream.concat(Stream.of(first, second), Stream.of(rest)))
        .isInStrictOrder(Comparator.<LogEntry>comparingInt(e -> indexIn(logged, e)));
  }

  private static int indexIn(ImmutableList<LogEntry> logged, LogEntry e) {
    int index = logged.indexOf(e);
    checkState(index >= 0, "log entry '%s' was not in the captured list: %s", e, logged);
    return index;
  }

  /**
   * Returns the Nth captured log entry from the start of the current test.
   *
   * <p>Warning: Overuse of this method is likely to result in brittle logs testing.
   *
   * <p>This method is provided for cases where exact log ordering is very well-defined, and
   * essential for correct testing. In general however, it is often better to use {@link
   * LogsSubject} via {@link TestingApi#assertLogs(LogMatcher...) assertLogs()} to identify expected
   * log statements, regardless of their exact index.
   *
   * <p>Instead of writing a test like:
   *
   * <pre>{@code
   * logs.assertLog(3).contains("Start Task: Foo");
   * logs.assertLog(6).contains("Update: Foo");
   * logs.assertLog(9).contains("End Task: Foo");
   * }</pre>
   *
   * <p>it is usually better to do something like:
   *
   * <pre>{@code
   * var start = logs.assertLogs().withMessageContaining("Start Task: Foo").getOnlyMatch();
   * var update = logs.assertLogs().withMessageContaining("Update: Foo").getOnlyMatch();
   * var end = logs.assertLogs().withMessageContaining("End Task: Foo").getOnlyMatch();
   * logs.assertLogOrder(start, update, end);
   * }</pre>
   */
  public LogSubject assertLog(int n) {
    return Truth.assertWithMessage("failure for log[%s]", n)
        .about(LogSubject.logs())
        .that(logged().get(n));
  }

  /**
   * Adds a post-test assertion, run automatically after the current test case finishes.
   *
   * <p>This method can be called either when the test API is created (e.g. when a JUnit rule or
   * extension is initialized) or during a test. Assertions are combined, in the order they were
   * added, and executed immediately after the test exits.
   *
   * <p>For example:
   *
   * <pre>{@code
   * @Rule
   * public final FloggerTestRule logs =
   *     FloggerTestRule.forClassUnderTest(INFO)
   *         .verify(logs -> logs.atOrAbove(WARNING).doNotOccur());
   * }</pre>
   */
  @CanIgnoreReturnValue
  public final ApiT verify(Consumer<LogsSubject> assertion) {
    this.verification = this.verification.andThen(checkNotNull(assertion));
    return api();
  }

  /**
   * Excludes one or more log entries from any post-test log verification. This method is useful
   * when you have a common logging policy (e.g. no warning logs) which you are deliberately
   * violating in a small number of cases.
   *
   * <p>Once a log entry is removed from verification, it cannot be reinstated, but you can still
   * make assertions on it within the test.
   */
  public final void excludeFromVerification(LogEntry entry, LogEntry... rest) {
    excluded.add(entry);
    excluded.addAll(asList(rest));
  }

  /**
   * Excludes a sequence of log entries from any post-test log verification. This method is useful
   * when you have a common logging policy (e.g. no warning logs) which you are deliberately
   * violating in a small number of cases.
   *
   * <p>Once a log entry is removed from verification, it cannot be reinstated, but you can still
   * make assertions on it within the test.
   */
  public final void excludeFromVerification(Iterable<LogEntry> entries) {
    entries.forEach(excluded::add);
  }

  /**
   * Removes all verification from this logs testing fixture. Unless new verification is added using
   * {@link #verify(Consumer)}, no additional assertions will be made after the test in which this
   * was called has exited.
   *
   * <p>Often, when only a small number of expected log entries are going to fail verification (e.g.
   * an explicitly expected warning log) it is better to call {@link
   * #excludeFromVerification(LogEntry, LogEntry...)} to avoid accidentally allowing other,
   * unexpected logs to go unverified.
   *
   * <p>This method will NOT remove any log entries from the "excluded" list, and any such entries
   * will also be ignored for any new post-test verification that's added. In general, it is not
   * recommended to use both {@link #excludeFromVerification}() and {@code clearVerification()} in
   * the same test as it is likely to make tests less readable.
   */
  public final void clearVerification() {
    this.verification = s -> {};
  }

  protected final ApiHook install(
      boolean useTestId, ImmutableMap<String, LevelClass> extraLevelMap) {
    return new ApiHook(useTestId, extraLevelMap);
  }

  public final class ApiHook implements AutoCloseable {
    private final List<Recorder> recorders = new ArrayList<>();
    private final String testId;
    private Closeable context;

    private ApiHook(boolean useTestId, ImmutableMap<String, LevelClass> extraLevelMap) {
      if (interceptor == null) {
        interceptor = BestInterceptorFactory.get();
      }
      // Empty string is a safe no-op value for the test ID.
      testId = useTestId ? TestId.claim() : "";
      Map<String, LevelClass> levelMap = mergeLevelMaps(defaultLevelMap, extraLevelMap);
      levelMap.forEach(
          (name, level) -> recorders.add(interceptor.attachTo(name, level, logs::add, testId)));
      context = FloggerBinding.maybeInstallFloggerContext(levelMap, testId);
    }

    @Override
    public void close() {
      if (context != null) {
        try {
          context.close();
        } catch (IOException e) {
          throw new AssertionError("context closeable cannot fail: ", e);
        }
        context = null;
      }
      // Recorders are defined to catch/ignore their own exceptions on close.
      recorders.forEach(Recorder::close);
      TestId.release(testId);
      LogsSubject subject = TestingApi.this.assertLogs();
      if (!excluded.isEmpty()) {
        subject =
            subject.matching(
                LogMatcher.of("notIn(<excluded>)", s -> s.filter(e -> !excluded.contains(e))));
      }
      verification.accept(subject);
    }
  }

  /**
   * Determines if the test ID of a log entry matches the given value. This is used to filter log
   * entries when tests are run in parallel to avoid capturing entries for the wrong tests.
   *
   * <p>Called from {@link LogInterceptor}, so as not to be part of this class's public API.
   */
  static boolean hasMatchingTestId(MessageAndMetadata mm, String testId) {
    ImmutableList<Object> values = mm.metadata().get(TEST_ID);
    // Assume logs without a detected test ID should still be collected (this may get interesting
    // in multi-threaded parallel tests, but it prevents in tagged logs being ignored).
    return values == null || values.contains(testId);
  }

  // Matches an expected text class name and captures the assumed class-under-test.
  private static final Pattern EXPECTED_TEST_CLASS_NAME =
      Pattern.compile("((?:[^.]+\\.)*[^.]+)Test");

  protected static String guessClassUnderTest(Class<?> caller) {
    String testClassName = caller.getName();
    Matcher matcher = EXPECTED_TEST_CLASS_NAME.matcher(testClassName);
    checkArgument(
        matcher.matches(),
        "Cannot infer class-under-test (test classes must be named 'XxxTest'): %s",
        testClassName);
    return matcher.group(1);
  }

  protected static String guessPackageUnderTest(Class<?> caller) {
    String packageName = caller.getPackage().getName();
    checkArgument(
        !packageName.isEmpty(),
        "Cannot infer package-under-test (test classes must not be in the root package): %s",
        caller.getName());
    return packageName;
  }

  // Approximate matcher to package names in Java:
  // Avoids bare class names, allows nested and inner classes (with '$').
  private static final Pattern PACKAGE_OR_CLASS_NAME =
      Pattern.compile("(?:[A-Z0-9_$]+\\.)+[A-Z0-9_$]+", CASE_INSENSITIVE);

  protected static ImmutableMap<String, LevelClass> getLevelMap(
      Class<?> testClass, ImmutableList<SetLogLevel> levels) {
    if (levels.isEmpty()) {
      return ImmutableMap.of();
    }
    ImmutableMap.Builder<String, LevelClass> builder = ImmutableMap.builder();
    for (SetLogLevel e : levels) {
      builder.put(extractTargetName(testClass, e), e.level());
    }
    return builder.buildOrThrow();
  }

  private static String extractTargetName(Class<?> testClass, SetLogLevel e) {
    String targetName;
    if (e.target() != Object.class) {
      checkArgument(
          e.name().isEmpty() && e.scope() == UNDEFINED,
          "specify only one of 'target', 'name' or 'scope': %s",
          e);
      targetName = e.target().getName();
    } else if (!e.name().isEmpty()) {
      checkArgument(
          e.scope() == UNDEFINED, "specify only one of 'target', 'name' or 'scope': %s", e);

      targetName = e.name();
    } else if (e.scope() != UNDEFINED) {
      switch (e.scope()) {
        case CLASS_UNDER_TEST:
          targetName = guessClassUnderTest(testClass);
          break;
        case PACKAGE_UNDER_TEST:
          targetName = guessPackageUnderTest(testClass);
          break;
        default:
          throw new AssertionError("unknown scope: " + e.scope());
      }
    } else {
      throw new IllegalArgumentException("specify one of 'target', 'name' or 'scope': " + e);
    }
    checkArgument(
        PACKAGE_OR_CLASS_NAME.matcher(targetName).matches(),
        "invalid target class or name (expected xxx.yyy.Zzz): %s",
        targetName);
    return targetName.replace('$', '.');
  }

  private static Map<String, LevelClass> mergeLevelMaps(
      ImmutableMap<String, LevelClass> defaultMap, ImmutableMap<String, LevelClass> extraMap) {
    if (extraMap.isEmpty()) {
      // Not a copy, but serves to narrow the value type.
      return ImmutableMap.copyOf(defaultMap);
    }
    if (defaultMap.isEmpty()) {
      return ImmutableMap.copyOf(extraMap);
    }
    HashMap<String, LevelClass> map = new HashMap<>();
    map.putAll(defaultMap);
    map.putAll(extraMap);
    return map;
  }

  // Lazy holder for caching the loaded interceptor.
  private static final class BestInterceptorFactory {
    private static final Supplier<LogInterceptor> factory =
        LogInterceptorLoader.loadBestInterceptorFactory();

    static LogInterceptor get() {
      return factory.get();
    }
  }

  /** A thread safe generator for short, unique test IDs. */
  @VisibleForTesting
  static class TestId {
    private static final ConcurrentSkipListSet<String> ids = new ConcurrentSkipListSet<>();
    private static final AtomicBoolean hasWarned = new AtomicBoolean();

    static String claim() {
      String id = "";
      if (ids.size() < 0x4000) {
        do {
          int n = ThreadLocalRandom.current().nextInt(0x10000);
          id = String.format("%04X", n);
        } while (!ids.add(id));
      } else if (hasWarned.compareAndSet(false, true)) {
        Logger.getLogger(TestingApi.class.getName())
            .warning(
                "Too many test IDs were generated; returning empty ID.\n"
                    + "This may cause multi-threaded logging test failures.");
      }
      return id;
    }

    static void release(String id) {
      if (!id.isEmpty()) {
        ids.remove(id);
      }
    }
  }
}
