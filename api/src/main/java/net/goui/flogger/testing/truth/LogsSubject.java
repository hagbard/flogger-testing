package net.goui.flogger.testing.truth;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static net.goui.flogger.testing.truth.MatchedLogsSubject.*;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;

/**
 * Fluent logs testing API for making assertions on a sequence of captured log entries.
 *
 * <p>The first part of a test assertion will typically filter captured logs using assertions such
 * as {@link #withMessageContaining(String)} or {@link #withLevelAtLeast(LevelClass)}.
 *
 * <p>Then assertions can be made by:
 *
 * <ul>
 *   <li>Calling a "getter" method such as {@link #getOnlyMatch()} to return one or more logs
 *       entries, on which further assertions can then be made.
 *   <li>Calling {@link #always()} or {@link #never()} to make assertions about the entire matched
 *       set of log entries.
 *   <li>Calling {@link #doNotOccur()} to directly assert that no log entries were matched.
 * </ul>
 *
 * <p>Because {@code LogsSubject} and {@code LogEntry} instances are immutable, and {@code
 * LogsSubject} operate on a snapshot of captured log entries, so it is safe to "split" a fluent
 * assertion for readability. For example:
 *
 * <pre>{@code
 * var assertWarnings = logs.assertLogs().withLevel(WARNING);
 * assertWarnings.matchCount().isGreaterThan(2);
 * assertWarnings.never().haveMetadata(REQUEST_ID, GOOD_TEST_ID);
 * assertWarnings.withMessageContaining("Read error").always().haveCause(IOException.class);
 * }</pre>
 *
 * <p>Note that by naming the local variable 'assertXxx', any following assertions read more
 * fluently.
 *
 * <p>The method {@link #doNotOccur()} is specifically designed to allow for easily asserting that
 * certain logs never occur, and it is particularly useful in conjunction with {@link
 * net.goui.flogger.testing.api.TestingApi#verify(java.util.function.Consumer)
 * verify(Consumer&lt;LogsSubject&gt;)}. For example:
 *
 * <pre>{@code
 * @Rule
 * public final FloggerTestRule logs =
 *     FloggerTestRule.forClassUnderTest(INFO)
 *         .verify(assertLogs -> assertLogs.atOrAbove(WARNING).doNotOccur());
 * }</pre>
 */
@CheckReturnValue
public final class LogsSubject extends Subject {
  private final ImmutableList<LogEntry> logs;

  private LogsSubject(FailureMetadata metadata, ImmutableList<LogEntry> logs) {
    super(metadata, logs);
    this.logs = logs;
  }

  public static Factory<LogsSubject, ImmutableList<LogEntry>> logSequences() {
    return LogsSubject::new;
  }

  /** Starts a fluent assertion about the current sequence of captured logs. */
  public static LogsSubject assertThat(ImmutableList<LogEntry> logs) {
    return assertAbout(logSequences()).that(logs);
  }

  private static ImmutableList<LogEntry> filter(
      ImmutableList<LogEntry> logs, Predicate<LogEntry> predicate) {
    return logs.stream().filter(predicate).collect(toImmutableList());
  }

  /** Matches the subsequence of captured logs with messages containing the specified substring. */
  public LogsSubject withMessageContaining(String fragment) {
    checkNotNull(fragment);
    return check("withMessageContaining('%s')", fragment)
        .about(logSequences())
        .that(filter(logs, e -> e.message().contains(fragment)));
  }

  /**
   * Matches the subsequence of captured logs with messages containing a match to the specified
   * regular expression.
   */
  public LogsSubject withMessageMatching(String regex) {
    Predicate<String> regexPredicate = Pattern.compile(regex).asPredicate();
    return check("withMessageMatching('%s')", regex)
        .about(logSequences())
        .that(filter(logs, e -> regexPredicate.test(e.message())));
  }

  /** Matches the subsequence of captured logs at the specified level. */
  public LogsSubject withLevel(LevelClass level) {
    return check("atLevel('%s')", level)
        .about(logSequences())
        .that(filter(logs, e -> e.levelClass() == level));
  }

  /** Matches the subsequence of captured logs strictly above the specified level. */
  public LogsSubject withLevelGreaterThan(LevelClass level) {
    return check("GreaterThanLevel('%s')", level)
        .about(logSequences())
        .that(filter(logs, e -> e.levelClass().compareTo(level) > 0));
  }

  /** Matches the subsequence of captured logs at or above the specified level. */
  public LogsSubject withLevelAtLeast(LevelClass level) {
    return check("AtLeastLevel('%s')", level)
        .about(logSequences())
        .that(filter(logs, e -> e.levelClass().compareTo(level) >= 0));
  }

  /** Matches the subsequence of captured logs strictly below the specified level. */
  public LogsSubject withLevelLessThan(LevelClass level) {
    return check("LessThanLevel('%s')", level)
        .about(logSequences())
        .that(filter(logs, e -> e.levelClass().compareTo(level) < 0));
  }

  /** Matches the subsequence of captured logs at or below the specified level. */
  public LogsSubject withLevelAtMost(LevelClass level) {
    return check("AtMostLevel('%s')", level)
        .about(logSequences())
        .that(filter(logs, e -> e.levelClass().compareTo(level) <= 0));
  }

  /** Matches the subsequence of captured logs with a cause of the specified type. */
  public LogsSubject withCause(Class<? extends Throwable> clazz) {
    return check("withCause(%s)", clazz.getName())
        .about(logSequences())
        .that(filter(logs, e -> clazz.isInstance(e.cause())));
  }

  /** Matches the subsequence of captured logs with the specified metadata key-value pair. */
  public LogsSubject withMetadata(String key, @Nullable Object value) {
    return withMetadataImpl(key, value);
  }

  /** Matches the subsequence of captured logs with the specified metadata key-value pair. */
  public LogsSubject withMetadata(String key, long value) {
    return withMetadataImpl(key, value);
  }

  /** Matches the subsequence of captured logs with the specified metadata key-value pair. */
  public LogsSubject withMetadata(String key, double value) {
    return withMetadataImpl(key, value);
  }

  /** Matches the subsequence of captured logs with the specified metadata key-value pair. */
  public LogsSubject withMetadata(String key, boolean value) {
    return withMetadataImpl(key, value);
  }

  private LogsSubject withMetadataImpl(String key, @Nullable Object value) {
    return check("withMetadata('%s', %s)", key, quoteIfString(value))
        .about(logSequences())
        .that(filter(logs, e -> e.hasMetadata(key, value)));
  }

  /** Matches the subsequence of captured logs with the specified metadata key. */
  public LogsSubject withMetadataKey(String key) {
    return check("withMetadataKey('%s')", key)
        .about(logSequences())
        .that(filter(logs, e -> e.hasMetadataKey(key)));
  }

  /**
   * Matches the subsequence of captured logs which came after the specified entry.
   *
   * <p>Note that when logs are captured in different threads, the order in which they appear may
   * not be the same at the order or their timestamps. This method does not attempt to examine
   * timestamps, and adheres only to the order in which logs are captured.
   *
   * @throws IllegalArgumentException if the given log entry is not in the currently matched
   *     sequence of entries.
   */
  public LogsSubject afterLog(LogEntry entry) {
    return check("afterLog('%s')", entry.snippet()).about(logSequences()).that(filterAfter(entry));
  }

  /**
   * Matches the subsequence of captured logs which came before the specified entry.
   *
   * <p>Note that when logs are captured in different threads, the order in which they appear may
   * not be the same at the order or their timestamps. This method does not attempt to examine
   * timestamps, and adheres only to the order in which logs are captured.
   *
   * @throws IllegalArgumentException if the given log entry is not in the currently matched
   *     sequence of entries.
   */
  public LogsSubject beforeLog(LogEntry entry) {
    return check("beforeLog('%s')", entry.snippet())
        .about(logSequences())
        .that(filterBefore(entry));
  }

  /** Allows a following assertion to be applied to every matched log entry. */
  public MatchedLogsSubject always() {
    return check("always()").about(allMatchedLogs()).that(logs);
  }

  /** Allows a following assertion to be applied to every matched log entry in a negative sense. */
  public MatchedLogsSubject never() {
    return check("never()").about(noMatchedLogs()).that(logs);
  }

  /** Asserts about the number of matched logs. */
  public IntegerSubject matchCount() {
    return check("matchCount()").that(logs.size());
  }

  /**
   * Equivalent to {@code matchCount().isEqualTo(0)}, but more readable for asserting that specific
   * logs do not occur.
   */
  public void doNotOccur() {
    if (!logs.isEmpty()) {
      failWithActual(simpleFact("was expected to be empty"));
    }
  }

  /**
   * Returns the Nth matched log entry, asserting that there are at least {@code (n + 1)} matched
   * log entries.
   */
  public LogEntry getMatch(int n) {
    checkArgument(n >= 0, "Match index must not be negative");
    if (n >= logs.size()) {
      failWithActual(simpleFact("expected at least " + (n + 1) + " matching logs"));
    }
    return logs.get(n);
  }

  /** Asserts that only one log entry is matched, and returns it. */
  public LogEntry getOnlyMatch() {
    Fact error = simpleFact("was expected to match exactly one log");
    if (logs.isEmpty()) {
      failWithoutActual(error, simpleFact("but was empty"));
    } else if (logs.size() > 1) {
      failWithActual(error);
    }
    return logs.get(0);
  }

  /** Returns the list of current matches without making any assertions. */
  public ImmutableList<LogEntry> getAllMatches() {
    return logs;
  }

  private ImmutableList<LogEntry> filterAfter(LogEntry entry) {
    int index = logs.indexOf(entry);
    checkArgument(index >= 0, "Provided log entry does not exist: %s", entry);
    return logs.subList(index + 1, logs.size());
  }

  private ImmutableList<LogEntry> filterBefore(LogEntry entry) {
    int index = logs.indexOf(entry);
    checkArgument(index >= 0, "Provided log entry does not exist: %s", entry);
    return logs.subList(0, index);
  }

  private static String quoteIfString(Object value) {
    return value instanceof String ? "'" + value + "'" : String.valueOf(value);
  }
}
