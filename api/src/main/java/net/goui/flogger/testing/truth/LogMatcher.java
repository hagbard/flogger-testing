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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.goui.flogger.testing.LogEntry;

/**
 * A named matcher which can be used by {@link
 * net.goui.flogger.testing.api.TestingApi#assertLogs(LogMatcher...) assertLogs()} to filter log
 * entries.
 *
 * <pre>{@code
 * LogEntry debugStart = ...;
 * logs.assertLogs(after(debugStart).inSameThread())).always().haveMetadataKey("debug_id");
 * }</pre>
 */
public class LogMatcher {
  @FunctionalInterface
  public interface LogEntryFilter extends UnaryOperator<Stream<LogEntry>> {
    static LogEntryFilter combine(LogEntryFilter before, LogEntryFilter after) {
      // Note: We cannot use andThen() here because we want to return a LogEntryFilter.
      return logs -> after.apply(before.apply(logs));
    }
  }

  private final String label;
  private final LogEntryFilter filter;

  public static LogMatcher of(String label, LogEntryFilter filter) {
    return new LogMatcher(label, filter);
  }

  public static LogMatcher simple(String name, Predicate<LogEntry> predicate) {
    return of(name + "()", logs -> logs.filter(predicate));
  }

  /**
   * Matches the subsequence of logs before the specified entry, in any thread. To restrict the
   * results to logs in the same thread as the target log, call {@link
   * ComparativeLogMatcher#inSameThread()} on the result.
   *
   * <p>Note that when logs are captured in different threads, the order in which they appear may
   * not be the same at the order or their timestamps. This method does not attempt to examine
   * timestamps, and adheres only to the order in which logs are captured.
   *
   * <p>If the given log entry does not exist in the sequence of captured logs being filtered, then
   * {@link IllegalStateException} is thrown during filtering.
   */
  public static ComparativeLogMatcher before(LogEntry entry) {
    return new ComparativeLogMatcher(label("before", entry), entry, LogMatcher::filterBefore);
  }

  /**
   * Matches the subsequence of logs after the specified entry, in any thread. To restrict the
   * results to logs in the same thread as the target log, call {@link
   * ComparativeLogMatcher#inSameThread()} on the result.
   *
   * <p>Note that when logs are captured in different threads, the order in which they appear may
   * not be the same at the order or their timestamps. This method does not attempt to examine
   * timestamps, and adheres only to the order in which logs are captured.
   *
   * <p>If the given log entry does not exist in the sequence of captured logs being filtered, then
   * {@link IllegalStateException} is thrown during filtering.
   */
  public static ComparativeLogMatcher after(LogEntry entry) {
    return new ComparativeLogMatcher(label("after", entry), entry, LogMatcher::filterAfter);
  }

  /**
   * Matches the subsequence of logs in the same thread as the specified entry.
   *
   * <p>This method can be combined with matchers such as {@link #after(LogEntry)}, and called via:
   *
   * <pre>{@code
   * logs.assertLogs(after(entry), inSameThreadAs(entry))...
   * }</pre>
   *
   * but for comparative testing, you may find it more readable to use {@link
   * ComparativeLogMatcher#inSameThread()}:
   *
   * <pre>{@code
   * logs.assertLogs(after(entry).inSameThread())...
   * }</pre>
   */
  public static LogMatcher inSameThreadAs(LogEntry entry) {
    return simple(label("inSameThreadAs", entry), e -> e.hasSameThreadAs(entry));
  }

  /**
   * Matches the subsequence of logs from the same outer class as the specified entry.
   *
   * <p>This is the default recommended way to test for logs "from the same source file" as other
   * logs, and while you can test for exact class matching (distinguishing nested and inner classes)
   * or even exact method matching, these risk making your tests more brittle than necessary.
   *
   * <p>Log entries with unknown class names are never considered equal (even to themselves).
   */
  public static LogMatcher fromSameOuterClassAs(LogEntry entry) {
    return simple(
        label("fromSameOuterClassAs", entry), e -> LogFilters.hasSameOuterClass(e, entry));
  }

  /**
   * Matches the subsequence of logs from the same class as the specified entry. Log statements
   * executed in lambdas or anonymous inner classes are considered to come from the first
   * non-synthetic (i.e. named) containing class, which can itself be a nested or inner class.
   *
   * <p>Warning: Using this matcher may make some tests more brittle than necessary in the face of
   * normal refactoring, so you may prefer to use {@link #fromSameOuterClassAs(LogEntry)} instead.
   *
   * <p>Log entries with unknown class names are never considered equal (even to themselves).
   */
  public static LogMatcher fromSameClassAs(LogEntry entry) {
    return simple(label("fromSameClassAs", entry), e -> LogFilters.hasSameClass(e, entry));
  }

  /**
   * Matches the subsequence of logs from the same class and method as the specified entry. Log
   * statements executed in lambdas or anonymous inner classes are considered to come from the first
   * non-synthetic (i.e. named) containing method.
   *
   * <p>Warning: Using this matcher may make some tests more brittle than necessary in the face of
   * normal refactoring, so you may prefer to use {@link #fromSameOuterClassAs(LogEntry)} instead.
   *
   * <p>Log entries with unknown class/method names are never considered equal (even to themselves).
   */
  public static LogMatcher fromSameMethodAs(LogEntry entry) {
    return simple(
        label("fromSameMethodAs", entry), e -> LogFilters.hasSameClassAndMethod(e, entry));
  }

  /**
   * Orders log entries for subsequent match operations in timestamp order. Note that if log entries
   * have identical timestamps, no guarantees are made about their eventual relative ordering.
   *
   * <p>If this method is combined with other matchers, the reordering of log entries will occur in
   * the same order as the matchers were listed.
   *
   * <p>Note that using this method in NOT guaranteed to result in log entries being seen in the
   * order that the log statements were invoked. In particular:
   *
   * <ul>
   *   <li>Log entries with identical timestamps due to time granularity may be reordered.
   *   <li>Reentrant logging may cause logs to be output in a different order to the acquisition of
   *       the timestamps.
   * </ul>
   *
   * <p>Over testing things like the precise order of log entries between different threads, may
   * result in brittle tests, and this method should be used only when necessary. Testing logs in
   * the default order they were captured by the test harness should almost always be sufficient,
   * and if multiple threads are being tested, consider using {@link #inSameThreadAs(LogEntry)
   * inSameThreadAs(entry)} or a comparative matcher such as {@link
   * ComparativeLogMatcher#after(LogEntry) after(entry)}.{@link ComparativeLogMatcher#inSameThread()
   * inSameThread()}
   */
  public static LogMatcher orderedByTimestamp() {
    return of("orderedByTimestamp()", logs -> logs.sorted(comparing(LogEntry::timeStamp)));
  }

  private static String label(String name, LogEntry entry) {
    return name + "(" + entry.snippet() + ")";
  }

  protected LogMatcher(String label, LogEntryFilter filter) {
    this.label = checkNotNull(label);
    this.filter = checkNotNull(filter);
  }

  public String getLabel() {
    return label;
  }

  public LogEntryFilter getFilter() {
    return filter;
  }

  private static Stream<LogEntry> filterBefore(Stream<LogEntry> logs, LogEntry entry) {
    ImmutableList<LogEntry> copy = logs.collect(toImmutableList());
    int index = copy.indexOf(entry);
    checkArgument(index >= 0, "Provided log entry does not exist: %s", entry);
    return copy.stream().limit(index);
  }

  private static Stream<LogEntry> filterAfter(Stream<LogEntry> logs, LogEntry entry) {
    ImmutableList<LogEntry> copy = logs.collect(toImmutableList());
    int index = copy.indexOf(entry);
    checkArgument(index >= 0, "Provided log entry does not exist: %s", entry);
    return copy.stream().skip(index + 1);
  }
}
