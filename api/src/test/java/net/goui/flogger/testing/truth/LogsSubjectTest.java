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
import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.LevelClass.FINE;
import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.LevelClass.SEVERE;
import static net.goui.flogger.testing.LevelClass.WARNING;
import static net.goui.flogger.testing.truth.LogMatcher.after;
import static net.goui.flogger.testing.truth.LogMatcher.before;
import static net.goui.flogger.testing.truth.LogsSubject.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogsSubjectTest {
  private static final Instant TIMESTAMP = Instant.now();
  private static final Object THREAD_ID = "<dummy>";

  @Test
  public void testMatching() {
    LogEntry first = log(INFO, "first");
    LogEntry second = log(INFO, "second");
    LogEntry third = log(INFO, "third");
    ImmutableList<LogEntry> logs = ImmutableList.of(first, second, third);

    assertMatched(assertThat(logs).matching(before(third)), first, second);
    assertMatched(assertThat(logs).matching(after(first)), second, third);
  }

  @Test
  public void testWithMessage() {
    LogEntry foo = log(INFO, "foo");
    LogEntry bar = log(INFO, "bar bar");
    LogEntry foobar = log(INFO, "foobar foo bar");
    ImmutableList<LogEntry> logs = ImmutableList.of(foo, bar, foobar);

    assertMatched(assertThat(logs).withMessageContaining("foo"), foo, foobar);
    assertMatched(assertThat(logs).withMessageContaining("bar"), bar, foobar);
    assertMatched(assertThat(logs).withMessageContaining("foobar"), foobar);

    assertMatched(assertThat(logs).withMessageContaining("foo", "foo"), foobar);
    assertMatched(assertThat(logs).withMessageContaining("bar", "bar"), bar, foobar);
    assertThat(logs).withMessageContaining("foobar", "foobar").doNotOccur();

    assertMatched(assertThat(logs).withMessageMatching("\\b[a-z]{3}\\b"), foo, bar, foobar);
    assertMatched(assertThat(logs).withMessageMatching("fo.*ar"), foobar);
  }

  @Test
  public void testWithLevel() {
    LogEntry fine = log(FINE, "fine");
    LogEntry info = log(INFO, "info");
    LogEntry warn = log(WARNING, "warn");
    ImmutableList<LogEntry> logs = ImmutableList.of(fine, info, warn);

    assertMatched(assertThat(logs).withLevel(INFO), info);
    assertMatched(assertThat(logs).withLevelAtLeast(INFO), info, warn);
    assertMatched(assertThat(logs).withLevelAtMost(INFO), fine, info);
    assertMatched(assertThat(logs).withLevelGreaterThan(INFO), warn);
    assertMatched(assertThat(logs).withLevelLessThan(INFO), fine);
  }

  @Test
  public void testWithCause() {
    LogEntry args = logWithCause(new IllegalArgumentException());
    LogEntry state = logWithCause(new IllegalStateException());
    LogEntry ioe = logWithCause(new IOException());
    LogEntry err = logWithCause(new OutOfMemoryError());
    LogEntry nocause = log(INFO, "info");
    ImmutableList<LogEntry> logs = ImmutableList.of(args, state, ioe, err, nocause);

    assertMatched(assertThat(logs).withCause(Throwable.class), args, state, ioe, err);
    assertMatched(assertThat(logs).withCause(RuntimeException.class), args, state);
    assertMatched(assertThat(logs).withCause(Error.class), err);
  }

  @Test
  public void testWithMetadata() {
    LogEntry strAndBool = logWithMetadata("key", "value", true);
    LogEntry strAndNum = logWithMetadata("key", "value", 12345L);
    LogEntry numOnly = logWithMetadata("key", 12345L);
    ImmutableList<LogEntry> logs = ImmutableList.of(strAndBool, strAndNum, numOnly);

    assertMatched(assertThat(logs).withMetadata("key", true), strAndBool);
    assertMatched(assertThat(logs).withMetadata("key", "value"), strAndBool, strAndNum);
    assertMatched(assertThat(logs).withMetadata("key", 12345), strAndNum, numOnly);

    assertMatched(assertThat(logs).withMetadataKey("key"), strAndBool, strAndNum, numOnly);
  }

  @Test
  public void testAlwaysAndNever() {
    LogEntry first = log(INFO, "start");
    LogEntry warn = log(WARNING, "error");
    LogEntry severe = log(SEVERE, "really bad error");
    LogEntry last = log(INFO, "end");
    ImmutableList<LogEntry> logs = ImmutableList.of(first, warn, severe, last);

    // The actual assertions are tested by MatchedLogsSubjectTest.
    assertThat(logs).withLevelAtLeast(WARNING).always().haveMessageContaining("error");
    // This isn't a great test in real code (any log might contain "error").
    assertThat(logs).withLevelLessThan(WARNING).never().haveMessageContaining("error");
  }

  @Test
  public void testDoNotOccur() {
    LogEntry first = log(INFO, "first");
    LogEntry second = log(INFO, "second");
    LogEntry third = log(INFO, "third");
    ImmutableList<LogEntry> logs = ImmutableList.of(first, second, third);

    assertThat(logs).withMessageContaining("fourth").doNotOccur();
    AssertionError fail =
        assertThrows(
            AssertionError.class,
            () -> assertThat(logs).withMessageContaining("second").doNotOccur());
    assertThat(fail).hasMessageThat().contains("was expected to be empty");
  }

  @Test
  public void testGetOnlyMatch() {
    LogEntry foo = log(INFO, "foo");
    LogEntry bar = log(INFO, "bar");
    LogEntry foobar = log(INFO, "foobar");
    ImmutableList<LogEntry> logs = ImmutableList.of(foo, bar, foobar);

    LogEntry actual = assertThat(logs).withMessageContaining("foobar").getOnlyMatch();
    assertThat(actual).isEqualTo(foobar);
    AssertionError fail =
        assertThrows(
            AssertionError.class,
            () -> assertThat(logs).withMessageContaining("foo").getOnlyMatch());
    assertThat(fail).hasMessageThat().contains("was expected to match exactly one log");
  }

  @Test
  public void testGetMatch() {
    LogEntry first = log(INFO, "first");
    LogEntry second = log(INFO, "second");
    LogEntry third = log(INFO, "third");
    ImmutableList<LogEntry> logs = ImmutableList.of(first, second, third);

    LogEntry actual = assertThat(logs).getMatch(1);
    assertThat(actual).isEqualTo(second);

    AssertionError fail = assertThrows(AssertionError.class, () -> assertThat(logs).getMatch(3));
    Truth.assertThat(fail).hasMessageThat().contains("expected at least 4 matching logs");

    assertThrows(IllegalArgumentException.class, () -> assertThat(logs).getMatch(-1));
  }

  private static void assertMatched(LogsSubject subject, LogEntry... logs) {
    Truth.assertThat(subject.getAllMatches()).containsExactlyElementsIn(logs).inOrder();
    subject.matchCount().isEqualTo(logs.length);
  }

  private static LogEntry log(LevelClass level, String message) {
    return LogEntry.of(
        "<class>",
        "<method>",
        level.name(),
        level,
        TIMESTAMP,
        THREAD_ID,
        message,
        ImmutableMap.of(),
        null);
  }

  private static LogEntry logWithCause(Throwable cause) {
    return LogEntry.of(
        "<class>",
        "<method>",
        WARNING.name(),
        WARNING,
        TIMESTAMP,
        THREAD_ID,
        "error",
        ImmutableMap.of(),
        cause);
  }

  private static LogEntry logWithMetadata(String key, Object... values) {
    checkArgument(
        Arrays.stream(values)
            .allMatch(
                v ->
                    v instanceof Long
                        || v instanceof Double
                        || v instanceof Boolean
                        || v instanceof String),
        "Metadata values expected to only be Long, Double, Boolean or String");
    return LogEntry.of(
        "<class>",
        "<method>",
        INFO.name(),
        INFO,
        TIMESTAMP,
        THREAD_ID,
        "message",
        ImmutableMap.of(key, ImmutableList.copyOf(values)),
        null);
  }
}
