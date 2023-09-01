package net.goui.flogger.testing.api;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.flogger.StackSize.MEDIUM;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;
import javax.annotation.CheckReturnValue;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.LogInterceptor.Recorder;
import net.goui.flogger.testing.truth.LogSubject;
import net.goui.flogger.testing.truth.LogsSubject;
import net.goui.flogger.testing.truth.ScopedLogsSubject;
import org.checkerframework.checker.nullness.qual.Nullable;

/** One of these is instantiated per test case. */
@CheckReturnValue
public class TestApi {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Tag label for a unique ID set for tests to support parallel testing.
  @VisibleForTesting
  static final String TEST_ID = "test_id";

  private final ImmutableMap<String, ? extends Level> levelMap;
  private LogInterceptor interceptor;
  // Captured logs (thread safe).
  private final ConcurrentLinkedQueue<LogEntry> logs = new ConcurrentLinkedQueue<>();
  private ImmutableList<LogEntry> logsSnapshot = ImmutableList.of();
  @Nullable private final Consumer<TestApi> commonAssertions;

  protected TestApi(
      Map<String, ? extends Level> levelMap,
      @Nullable LogInterceptor interceptor,
      @Nullable Consumer<TestApi> commonAssertions) {
    this.levelMap = ImmutableMap.copyOf(levelMap);
    this.interceptor = interceptor;
    this.commonAssertions = commonAssertions;
  }

  private ImmutableList<LogEntry> logged() {
    if (logsSnapshot.size() != logs.size()) {
      // Copying the concurrent queue is thread safe.
      logsSnapshot = ImmutableList.copyOf(logs);
    }
    return logsSnapshot;
  }

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

  public ScopedLogsSubject assertLogs() {
    return ScopedLogsSubject.assertThat(logged());
  }

  public LogSubject assertLog(int n) {
    return Truth.assertWithMessage("failure for log[%s]", n)
        .about(LogSubject.logEntries())
        .that(logged().get(n));
  }

  public LogsSubject assertThat() {
    return LogsSubject.assertThat(logged());
  }

  protected final ImmutableMap<String, ? extends Level> levelMap() {
    return levelMap;
  }

  protected final LogInterceptor interceptor() {
    return interceptor;
  }

  protected final Consumer<TestApi> commonAssertions() {
    return commonAssertions;
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
      if (commonAssertions != null) {
        commonAssertions.accept(TestApi.this);
      }
    }
  }

  /**
   * Determines if the test ID of a log entry matches the given value. This is used to filter log
   * entries when tests are run in parallel to avoid capturing entries for the wrong tests.
   *
   * <p>Called from {@link LogInterceptor} so as not to be part of this class's public API.
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
