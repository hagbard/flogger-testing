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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.truth.LogMatcher.after;
import static net.goui.flogger.testing.truth.LogMatcher.before;
import static net.goui.flogger.testing.truth.LogMatcher.inSameThreadAs;
import static net.goui.flogger.testing.truth.LogMatcher.orderedByTimestamp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogMatcherTest {
  private static final Instant TIMESTAMP = Instant.now();
  private static final Object THREAD_FOO = "<foo>";
  private static final Object THREAD_BAR = "<bar>";

  @Test
  public void simple() {}

  @Test
  public void testBeforeAndAfter() {
    LogEntry first = log(INFO, "first", THREAD_FOO);
    LogEntry second = log(INFO, "second", THREAD_FOO);
    LogEntry other = log(INFO, "other thread", THREAD_BAR);
    LogEntry third = log(INFO, "third", THREAD_FOO);
    ImmutableList<LogEntry> logs = ImmutableList.of(first, second, other, third);

    assertMatched(before(third), logs, first, second, other);
    assertMatched(after(first), logs, second, other, third);
    assertMatched(before(third).inSameThread(), logs, first, second);
    assertMatched(after(first).inSameThread(), logs, second, third);
  }

  @Test
  public void testInSameThread() {
    LogEntry first = log(INFO, "first", THREAD_FOO);
    LogEntry second = log(INFO, "second", THREAD_FOO);
    LogEntry other = log(INFO, "other thread", THREAD_BAR);
    LogEntry third = log(INFO, "third", THREAD_FOO);
    ImmutableList<LogEntry> logs = ImmutableList.of(first, second, other, third);

    assertMatched(inSameThreadAs(first), logs, first, second, third);
    assertMatched(inSameThreadAs(other), logs, other);
  }

  @Test
  public void testOrderedByTimestamp() {
    LogEntry first = logWithTimestamp("first", Duration.ofSeconds(1));
    LogEntry second = logWithTimestamp("second", Duration.ofSeconds(2));
    LogEntry tiedSecond = logWithTimestamp("tied", Duration.ofSeconds(2));
    LogEntry third = logWithTimestamp("third", Duration.ofSeconds(3));
    ImmutableList<LogEntry> logs = ImmutableList.of(third, tiedSecond, second, first);

    assertMatched(orderedByTimestamp(), logs, first, tiedSecond, second, third);
  }

  private static void assertMatched(
      LogMatcher matcher, ImmutableList<LogEntry> logs, LogEntry... expected) {
    ImmutableList<LogEntry> filtered =
        matcher.getFilter().apply(logs.stream()).collect(toImmutableList());
    assertThat(filtered).containsExactly((Object[]) expected).inOrder();
  }

  private static LogEntry log(LevelClass level, String message, Object threadId) {
    return LogEntry.of(
        "<class>",
        "<method>",
        level.name(),
        level,
        TIMESTAMP,
        threadId,
        message,
        ImmutableMap.of(),
        null);
  }

  private static LogEntry logWithTimestamp(String message, Duration delay) {
    return LogEntry.of(
        "<class>",
        "<method>",
        INFO.name(),
        INFO,
        TIMESTAMP.plus(delay),
        "<thread ID>",
        message,
        ImmutableMap.of(),
        null);
  }
}
