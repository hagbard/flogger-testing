/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

 This program and the accompanying materials are made available under the terms of the
 Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

 SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.truth;

import static com.google.common.base.Preconditions.checkArgument;
import static net.goui.flogger.testing.LevelClass.*;
import static net.goui.flogger.testing.truth.LogSubject.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogSubjectTest {
  private static final Instant TIMESTAMP = Instant.now();
  private static final Object THREAD_ID = "<dummy>";

  @Test
  public void testMessage() {
    LogEntry e = log(INFO, "the quick brown fox jumps");
    assertThat(e).hasMessageContaining("brown fox");
    assertThat(e).hasMessageMatching("quick.*fox");

    assertThat(e).hasMessageContaining("the", "brown", "jumps");
    assertThat(e).hasMessageContaining("he", "row", "jump");
    assertThat(e).hasMessageMatching("\\bquick brown\\b");

    assertThrows(AssertionError.class, () -> assertThat(e).hasMessageContaining("orange"));
    assertThrows(AssertionError.class, () -> assertThat(e).hasMessageMatching("orange"));

    assertThrows(NullPointerException.class, () -> assertThat(e).hasMessageContaining(null));
    assertThrows(NullPointerException.class, () -> assertThat(e).hasMessageMatching(null));

    assertThrows(IllegalArgumentException.class, () -> assertThat(e).hasMessageContaining(""));
    assertThrows(
        IllegalArgumentException.class, () -> assertThat(e).hasMessageContaining("foo", ""));
    assertThrows(IllegalArgumentException.class, () -> assertThat(e).hasMessageMatching(""));
  }

  @Test
  public void testLevel() {
    LogEntry info = log(INFO, "the quick brown fox");
    LogEntry warn = log(WARNING, "the lazy dog");
    assertThat(info).hasLevel(INFO);
    assertThrows(AssertionError.class, () -> assertThat(warn).hasLevel(INFO));

    assertThat(info).hasLevelLessThan(WARNING);
    assertThrows(AssertionError.class, () -> assertThat(warn).hasLevelLessThan(WARNING));

    assertThat(info).hasLevelGreaterThan(FINE);
    assertThat(warn).hasLevelGreaterThan(FINE);

    assertThat(info).hasLevelAtMost(INFO);
    assertThrows(AssertionError.class, () -> assertThat(warn).hasLevelAtMost(INFO));

    assertThat(info).hasLevelAtLeast(INFO);
    assertThat(warn).hasLevelAtLeast(INFO);
  }

  @Test
  public void testCause() {
    LogEntry warn = log(WARNING, "the quick brown fox", new IllegalArgumentException("Oh dear!"));
    LogEntry info = log(INFO, "the lazy dog");

    assertThat(warn).hasCause(IllegalArgumentException.class);
    assertThat(warn).hasCause(RuntimeException.class);
    assertThrows(AssertionError.class, () -> assertThat(warn).hasCause(IOException.class));

    assertThrows(NullPointerException.class, () -> assertThat(info).hasCause(null));
    assertThat(info).cause().isNull();

    assertThat(warn).cause().hasMessageThat().contains("dear");
    assertThrows(
        NullPointerException.class,
        () -> assertThat(info).cause().hasMessageThat().contains("dear"));
  }

  @Test
  public void testMetadata() {
    LogEntry e = logWithMetadata("key", "false", "42.0", 123456L, 54321D, Math.PI, true);

    // This uses LogEntry.hasMetadata() behind the scenes (which is well tested), so just have
    // sanity checks here.
    assertThat(e).hasMetadata("key", "false");
    assertThat(e).hasMetadata("key", "42.0");
    assertThat(e).hasMetadata("key", 123456);
    assertThat(e).hasMetadata("key", 54321);
    assertThat(e).hasMetadata("key", 3.1415926536);
    assertThat(e).hasMetadata("key", true);
    assertThat(e).hasMetadataKey("key");
  }

  private static LogEntry log(LevelClass level, String message) {
    return log(level, message, null);
  }

  private static LogEntry log(LevelClass level, String message, @Nullable Throwable cause) {
    return LogEntry.of(
        "<class>",
        "<method>",
        level.name(),
        level,
        TIMESTAMP,
        THREAD_ID,
        message,
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
