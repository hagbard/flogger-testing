package net.goui.flogger.testing.api;

import static com.google.common.base.Preconditions.*;
import static com.google.common.flogger.StackSize.MEDIUM;
import static com.google.common.truth.StreamSubject.streams;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopedLoggingContext.LoggingContextCloseable;
import com.google.common.flogger.context.ScopedLoggingContexts;
import com.google.common.flogger.context.Tags;
import com.google.common.truth.Truth;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.LogInterceptor.Recorder;
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
 *   <li>Filtering logs via {@link LogsSubject}; {@code withMessageContaining()}, {@code withLevel()}
 *       ...
 *   <li>Asserting over all logs via {@link MatchedLogsSubject
 *       AllLogsSubject}; {@code haveMessageContaining()}, {@code haveLevel()} ...
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
 * test, the instance must be mutable. It also means that things like the {@link LogLevelMap} cannot
 * be modified after the instance is installed.
 */
@CheckReturnValue
public abstract class TestingApi<ApiT extends TestingApi<ApiT>> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Tag label for a unique ID set for tests to support parallel testing.
  @VisibleForTesting static final String TEST_ID = "test_id";

  private final ImmutableMap<String, ? extends Level> levelMap;
  private LogInterceptor interceptor;
  // Captured logs (thread safe).
  private final ConcurrentLinkedQueue<LogEntry> logs = new ConcurrentLinkedQueue<>();
  private ImmutableList<LogEntry> logsSnapshot = ImmutableList.of();
  private Consumer<LogsSubject> verification;

  protected TestingApi(
      Map<String, ? extends Level> levelMap, @Nullable LogInterceptor interceptor) {
    this.levelMap = ImmutableMap.copyOf(levelMap);
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
  public LogsSubject assertLogs() {
    return LogsSubject.assertThat(logged());
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
   * LogsSubject} via {@link #assertLogs()} to identify expected log statements, regardless of their
   * exact index.
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

  protected final ApiHook install(boolean useTestId) {
    return new ApiHook(useTestId);
  }

  public final class ApiHook implements AutoCloseable {
    private final List<Recorder> recorders = new ArrayList<>();
    private final LoggingContextCloseable context;
    private final String testId;

    private ApiHook(boolean useTestId) {
      if (interceptor == null) {
        interceptor = BestInterceptorFactory.get();
      }
      // Empty string is a safe no-op value for the test ID.
      testId = useTestId ? TestId.claim() : "";
      levelMap.forEach(
          (name, level) -> recorders.add(interceptor.attachTo(name, level, logs::add, testId)));
      // Skip adding test tags if the given ID is empty.
      Tags testTag = !testId.isEmpty() ? Tags.of(TEST_ID, testId) : Tags.empty();
      context =
          ScopedLoggingContexts.newContext()
              .withLogLevelMap(LogLevelMap.create(levelMap))
              .withTags(testTag)
              .install();
    }

    @Override
    public void close() {
      context.close();
      // Recorders are defined to catch/ignore their own exceptions on close.
      recorders.forEach(Recorder::close);
      TestId.release(testId);
      verification.accept(TestingApi.this.assertLogs());
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

    static String claim() {
      String id = "";
      if (ids.size() < 0x4000) {
        do {
          int n = ThreadLocalRandom.current().nextInt(0x10000);
          id = String.format("%04X", n);
        } while (!ids.add(id));
      } else {
        logger.atWarning().atMostEvery(30, SECONDS).withStackTrace(MEDIUM).log(
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