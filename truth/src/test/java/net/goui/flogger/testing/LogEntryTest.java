package net.goui.flogger.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogEntryTest {
  @Test
  public void testInit() {
    ImmutableMap<String, ImmutableList<Object>> metadata =
        ImmutableMap.of("foo", ImmutableList.of("bar"));
    Throwable cause = new RuntimeException();
    LogEntry entry =
        LogEntry.of(
            "<class>", "<method>", "<info>", LevelClass.INFO, "log message", metadata, cause);
    assertThat(entry.className()).isEqualTo("<class>");
    assertThat(entry.methodName()).isEqualTo("<method>");
    assertThat(entry.levelName()).isEqualTo("<info>");
    assertThat(entry.levelClass()).isEqualTo(LevelClass.INFO);
    assertThat(entry.message()).isEqualTo("log message");
    assertThat(entry.getMetadata()).isEqualTo(metadata);
    assertThat(entry.cause()).isEqualTo(cause);
  }

  @Test
  public void testInit_optionalArguments() {
    LogEntry entry =
        LogEntry.of(null, null, "<info>", LevelClass.INFO, "log message", ImmutableMap.of(), null);
    assertThat(entry.className()).isEqualTo("<unknown>");
    assertThat(entry.methodName()).isEqualTo("<unknown>");
    assertThat(entry.cause()).isNull();
  }

  @Test
  public void testToString() {
    ImmutableMap<String, ImmutableList<Object>> metadata =
            ImmutableMap.of("foo", ImmutableList.of("bar"));
    Throwable cause = new RuntimeException();
    LogEntry entry =
            LogEntry.of(
                    "<class>", "<method>", "<info>", LevelClass.INFO, "log message", metadata, cause);
    assertThat(entry.toString()).contains("<class>#<method>@<info>(INFO)");
    assertThat(entry.toString()).contains("log message");
    assertThat(entry.toString()).contains("RuntimeException");
    assertThat(entry.toString()).contains("foo=[bar]");
  }
}
