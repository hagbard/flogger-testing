/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.truth;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static java.util.stream.Collectors.joining;
import static net.goui.flogger.testing.truth.LogFilters.containsAllFragmentsInOrder;
import static net.goui.flogger.testing.truth.LogFilters.joinFragments;
import static net.goui.flogger.testing.truth.MatchedLogsSubject.allMatchedLogs;
import static net.goui.flogger.testing.truth.MatchedLogsSubject.noMatchedLogs;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.truth.LogMatcher.LogEntryFilter;

/**
 * Fluent logs testing API for making assertions on a sequence of captured log entries.
 *
 * <p>The first part of a test assertion will typically filter captured logs using assertions such
 * as {@link #withMessageContaining(String, String...)} or {@link #withLevelAtLeast(LevelClass)}.
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
  private final boolean allowEmptyMatch;

  private LogsSubject(
      FailureMetadata metadata, ImmutableList<LogEntry> logs, boolean allowEmptyMatch) {
    super(metadata, logs);
    this.logs = logs;
    this.allowEmptyMatch = allowEmptyMatch;
  }

  /** Returns a factory for a logs subject about the complete set of captured logs. */
  public static Factory<LogsSubject, ImmutableList<LogEntry>> logSequences() {
    return logSequences(/* allowEmptyMatch */ false);
  }

  /** Returns a factory about the current set of filtered logs, inheriting configuration. */
  private Factory<LogsSubject, ImmutableList<LogEntry>> currentLogs() {
    return logSequences(allowEmptyMatch);
  }

  /** Returns a factory with the specified behaviour configuration. */
  private static Factory<LogsSubject, ImmutableList<LogEntry>> logSequences(
      boolean allowEmptyMatch) {
    return (m, l) -> new LogsSubject(m, l, allowEmptyMatch);
  }

  /** Starts a fluent assertion about the current sequence of captured logs. */
  public static LogsSubject assertThat(ImmutableList<LogEntry> logs) {
    return assertAbout(logSequences()).that(logs);
  }

  private static ImmutableList<LogEntry> filter(
      ImmutableList<LogEntry> logs, Predicate<LogEntry> predicate) {
    return logs.stream().filter(predicate).collect(toImmutableList());
  }

  public LogsSubject matching(LogMatcher... matchers) {
    if (matchers.length == 0) {
      return this;
    }
    String label =
        "matching"
            + Arrays.stream(matchers).map(LogMatcher::getLabel).collect(joining(",", "(", ")"));
    LogEntryFilter combined =
        Arrays.stream(matchers).map(LogMatcher::getFilter).reduce(x -> x, LogEntryFilter::combine);
    return check(label)
        .about(currentLogs())
        .that(combined.apply(logs.stream()).collect(toImmutableList()));
  }

  /**
   * Matches the subsequence of captured logs with messages containing all the specified text
   * fragments in the given order. Matches are case-sensitive and do not delimit word boundaries.
   *
   * <p>A log entry is only matched if its message contains all the given fragments in the given
   * order (not including cases where overlap occurs). For example:
   *
   * <ul>
   *   <li>"Hello World" is matched by {@code ("Hello", "World")}, but not {@code ("World",
   *       "Hello")}.
   *   <li>"Foo Bar Baz" is matched by {@code ("Ba", "Ba")}, but not {@code ("Fo", "Fo")}.
   *   <li>"Information" is not matched by {@code ("Inform", "formation")}.
   * </ul>
   */
  public LogsSubject withMessageContaining(String fragment, String... rest) {
    return check("withMessageContaining(%s)", joinFragments(fragment, rest))
        .about(currentLogs())
        .that(filter(logs, e -> containsAllFragmentsInOrder(e.message(), fragment, rest)));
  }

  /**
   * Matches the subsequence of captured logs with messages containing a match to the specified
   * regular expression.
   */
  public LogsSubject withMessageMatching(String regex) {
    Predicate<String> regexPredicate = Pattern.compile(regex).asPredicate();
    return check("withMessageMatching('%s')", regex)
        .about(currentLogs())
        .that(filter(logs, e -> regexPredicate.test(e.message())));
  }

  /** Matches the subsequence of captured logs at the specified level. */
  public LogsSubject withLevel(LevelClass level) {
    return check("withLevel(%s)", level)
        .about(currentLogs())
        .that(filter(logs, e -> e.levelClass() == level));
  }

  /** Matches the subsequence of captured logs strictly above the specified level. */
  public LogsSubject withLevelGreaterThan(LevelClass level) {
    return check("withLevelGreaterThan(%s)", level)
        .about(currentLogs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) > 0));
  }

  /** Matches the subsequence of captured logs at or above the specified level. */
  public LogsSubject withLevelAtLeast(LevelClass level) {
    return check("withLevelAtLeast(%s)", level)
        .about(currentLogs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) >= 0));
  }

  /** Matches the subsequence of captured logs strictly below the specified level. */
  public LogsSubject withLevelLessThan(LevelClass level) {
    return check("withLevelLessThan(%s)", level)
        .about(currentLogs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) < 0));
  }

  /** Matches the subsequence of captured logs at or below the specified level. */
  public LogsSubject withLevelAtMost(LevelClass level) {
    return check("withLevelAtMost(%s)", level)
        .about(currentLogs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) <= 0));
  }

  /** Matches the subsequence of captured logs with a cause of the specified type. */
  public LogsSubject withCause(Class<? extends Throwable> clazz) {
    return check("withCause(%s)", clazz.getName())
        .about(currentLogs())
        .that(filter(logs, e -> clazz.isInstance(e.cause())));
  }

  /** Matches the subsequence of captured logs with a cause of any type. */
  public LogsSubject withCause() {
    return check("withCause()").about(currentLogs()).that(filter(logs, e -> e.cause() != null));
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
        .about(currentLogs())
        .that(filter(logs, e -> e.hasMetadata(key, value)));
  }

  /** Matches the subsequence of captured logs with the specified metadata key. */
  public LogsSubject withMetadataKey(String key) {
    return check("withMetadataKey('%s')", key)
        .about(currentLogs())
        .that(filter(logs, e -> e.hasMetadataKey(key)));
  }

  /**
   * Modifies this logs subject to permit trivial assertions against empty sequences of log entries.
   * In general, you should avoid this method unless it is absolutely necessary, since it can create
   * misleading tests which appear to verify events that may never actually occur.
   *
   * <p>This permits occasionally useful assertions about logs which might not appear for certain
   * reasons. For example:
   *
   * <pre>{@code
   * // The code under test should recover from low memory and emit this warning (but we cannot
   * // reliably cause it to occur).
   * logs.assertLogs()
   *     .withLevel(SEVERE)
   *     .withMessageContaining("Low Memory")
   *     .allowingNoMatches()
   *     .always()
   *     .hasCause(OutOfMemoryError.class);
   * }</pre>
   */
  public LogsSubject allowingNoMatches() {
    return check("allowingNoMatches()").about(logSequences(/* allowEmptyMatch= */ true)).that(logs);
  }

  /**
   * Allows a following assertion to be applied to every currently matched log entry.
   *
   * <p>>Note: Unless {@link #allowingNoMatches()} is used prior to calling this method, this method
   * will fail if there are no matched log entries.
   */
  public MatchedLogsSubject always() {
    checkForEmptyLogs();
    return check("always()").about(allMatchedLogs()).that(logs);
  }

  /**
   * Allows a following assertion to be applied to every matched log entry in a negative sense.
   *
   * <p>>Note: Unless {@link #allowingNoMatches()} is used prior to calling this method, this method
   * will fail if there are no matched log entries.
   */
  public MatchedLogsSubject never() {
    checkForEmptyLogs();
    return check("never()").about(noMatchedLogs()).that(logs);
  }

  /**
   * Asserts about the number of matched logs.
   *
   * <p>>Note: Unless {@link #allowingNoMatches()} is used prior to calling this method, this method
   * will fail if there are no matched log entries.
   */
  public IntegerSubject matchCount() {
    checkForEmptyLogs();
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

  private void checkForEmptyLogs() {
    if (!allowEmptyMatch && logs.isEmpty()) {
      failWithoutActual(
          simpleFact(
              "no log entries were matched"
                  + " (to test potentially empty sequences, use 'allowingNoMatches()')"));
    }
  }

  private static String quoteIfString(Object value) {
    return value instanceof String ? "'" + value + "'" : String.valueOf(value);
  }
}
