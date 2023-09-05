package net.goui.flogger.testing.truth;

import static com.google.common.base.Preconditions.checkArgument;
import static net.goui.flogger.testing.LevelClass.*;
import static net.goui.flogger.testing.truth.MatchedLogsSubject.allMatchedLogs;
import static net.goui.flogger.testing.truth.MatchedLogsSubject.noMatchedLogs;
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
public class MatchedLogsSubjectTest {
  private static final Instant TIMESTAMP = Instant.now();

  private static MatchedLogsSubject assertNo(ImmutableList<LogEntry> logs) {
    return Truth.assertAbout(noMatchedLogs()).that(logs);
  }

  private static MatchedLogsSubject assertAll(ImmutableList<LogEntry> logs) {
    return Truth.assertAbout(allMatchedLogs()).that(logs);
  }

  @Test
  public void haveMessageContaining() {
    LogEntry foo = log(INFO, "foo");
    LogEntry fooBar = log(INFO, "foo bar");
    LogEntry fooBaz = log(INFO, "foo baz");
    ImmutableList<LogEntry> logs = ImmutableList.of(foo, fooBar, fooBaz);

    assertAll(logs).haveMessageContaining("foo");
    assertAll(logs).haveMessageMatching("\\bfoo\\b");
    assertNo(logs).haveMessageContaining("quux");
    assertNo(logs).haveMessageMatching("[0-9]");

    // Some conditions are false either way around.
    assertThrows(AssertionError.class, () -> assertAll(logs).haveMessageMatching("bar"));
    assertThrows(AssertionError.class, () -> assertNo(logs).haveMessageMatching("bar"));
  }

  @Test
  public void haveLevel() {
    LogEntry fine = log(FINE, "fine");
    LogEntry info = log(INFO, "info");
    LogEntry warn = log(WARNING, "warn");
    ImmutableList<LogEntry> logs = ImmutableList.of(fine, info, warn);

    assertAll(logs).haveLevelAtLeast(FINE);
    assertAll(logs).haveLevelAtMost(WARNING);
    assertAll(logs).haveLevelLessThan(SEVERE);
    assertAll(logs).haveLevelGreaterThan(FINEST);

    assertNo(logs).haveLevel(SEVERE);
    assertNo(logs).haveLevelLessThan(FINE);

    assertThrows(AssertionError.class, () -> assertAll(logs).haveLevel(INFO));
    assertThrows(AssertionError.class, () -> assertNo(logs).haveLevel(INFO));
  }

  @Test
  public void haveCause() {
    LogEntry args = logWithCause(new IllegalArgumentException());
    LogEntry state = logWithCause(new IllegalStateException());
    ImmutableList<LogEntry> logs = ImmutableList.of(args, state);

    assertAll(logs).haveCause(RuntimeException.class);
    assertNo(logs).haveCause(IOException.class);

    assertThrows(
        AssertionError.class, () -> assertAll(logs).haveCause(IllegalArgumentException.class));
    assertThrows(
        AssertionError.class, () -> assertNo(logs).haveCause(IllegalArgumentException.class));
  }

  @Test
  public void haveMetadata() {
    LogEntry strAndBool = logWithMetadata("key", "value", true);
    LogEntry strAndNum = logWithMetadata("key", "value", 12345L);
    ImmutableList<LogEntry> logs = ImmutableList.of(strAndBool, strAndNum);

    assertAll(logs).haveMetadata("key", "value");
    assertNo(logs).haveMetadata("foo", "bar");
    assertAll(logs).haveMetadataKey("key");
    assertNo(logs).haveMetadataKey("foo");

    assertThrows(AssertionError.class, () -> assertAll(logs).haveMetadata("key", 12345));
    assertThrows(AssertionError.class, () -> assertNo(logs).haveMetadata("key", 12345));
  }

  private static LogEntry log(LevelClass level, String message) {
    return LogEntry.of(
        "<class>", "<method>", level.name(), level, TIMESTAMP, message, ImmutableMap.of(), null);
  }

  private static LogEntry logWithCause(Throwable cause) {
    return LogEntry.of(
        "<class>",
        "<method>",
        WARNING.name(),
        WARNING,
        TIMESTAMP,
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
        "message",
        ImmutableMap.of(key, ImmutableList.copyOf(values)),
        null);
  }
}
