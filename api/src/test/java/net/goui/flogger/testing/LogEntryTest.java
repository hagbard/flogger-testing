package net.goui.flogger.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogEntryTest {
  private static final Instant TEST_TIMESTAMP = Instant.now();

  @Test
  public void testAllArguments() {
    ImmutableMap<String, ImmutableList<Object>> metadata =
        ImmutableMap.of("foo", ImmutableList.of("bar"));
    Throwable cause = new RuntimeException();
    LogEntry entry =
        LogEntry.of(
            "<class>",
            "<method>",
            "<info>",
            LevelClass.INFO,
            TEST_TIMESTAMP,
            "log message",
            metadata,
            cause);
    assertThat(entry.className()).isEqualTo("<class>");
    assertThat(entry.methodName()).isEqualTo("<method>");
    assertThat(entry.levelName()).isEqualTo("<info>");
    assertThat(entry.levelClass()).isEqualTo(LevelClass.INFO);
    assertThat(entry.timeStamp()).isEqualTo(TEST_TIMESTAMP);
    assertThat(entry.message()).isEqualTo("log message");
    assertThat(entry.metadata()).isEqualTo(metadata);
    assertThat(entry.cause()).isEqualTo(cause);
  }

  @Test
  public void testOptionalArguments() {
    LogEntry entry =
        LogEntry.of(
            null,
            null,
            "<info>",
            LevelClass.INFO,
            TEST_TIMESTAMP,
            "log message",
            ImmutableMap.of(),
            null);
    assertThat(entry.className()).isEqualTo("<unknown>");
    assertThat(entry.methodName()).isEqualTo("<unknown>");
    assertThat(entry.metadata()).isEmpty();
    assertThat(entry.cause()).isNull();
  }

  @Test
  public void testHasMetadata() {
    LogEntry e =
        LogEntry.of(
            null,
            null,
            "<info>",
            LevelClass.INFO,
            TEST_TIMESTAMP,
            "<unused>",
            ImmutableMap.of(
                "key", values("key", "false", "42.0", 123456L, 54321D, Math.PI, true),
                "empty", values()),
            null);

    // Passes with explicit string value, but not a boolean, integer or double.
    assertThat(e.hasMetadata("key", "false")).isTrue();
    assertThat(e.hasMetadata("key", "42.0")).isTrue();
    assertThat(e.hasMetadata("key", false)).isFalse();
    assertThat(e.hasMetadata("key", 42)).isFalse();
    assertThat(e.hasMetadata("key", 42.0)).isFalse();

    // Passes based on the toString() representation of the instance, not its exact value.
    Object falsey =
        new Object() {
          @Override
          public String toString() {
            return "false";
          }
        };
    assertThat(e.hasMetadata("key", falsey)).isTrue();

    // Passes even though the value in the metadata is a long.
    assertThat(e.hasMetadata("key", 123456)).isTrue();

    // Passes even though the value in the metadata is a double (it is an exact numerical match).
    assertThat(e.hasMetadata("key", 54321)).isTrue();

    // Passes using BigDecimal and BigInteger.
    assertThat(e.hasMetadata("key", BigDecimal.valueOf(123456.0))).isTrue();
    assertThat(e.hasMetadata("key", BigInteger.valueOf(54321))).isTrue();

    // Passes with 10 digits of precision, but fails with 9.
    assertThat(e.hasMetadata("key", 3.1415926536)).isTrue();
    assertThat(e.hasMetadata("key", 3.141592654)).isFalse();

    // Passes with boolean value, but not with string representation.
    assertThat(e.hasMetadata("key", true)).isTrue();
    assertThat(e.hasMetadata("key", "true")).isFalse();

    // Test metadata key containment (even without values, keys can exist in metadata).
    assertThat(e.hasMetadataKey("key")).isTrue();
    assertThat(e.hasMetadataKey("empty")).isTrue();
    assertThat(e.hasMetadataKey("nope")).isFalse();
  }

  @Test
  public void testToString() {
    ImmutableMap<String, ImmutableList<Object>> metadata =
        ImmutableMap.of("foo", ImmutableList.of("bar"));
    Throwable cause = new RuntimeException();
    LogEntry entry =
        LogEntry.of(
            "<class>",
            "<method>",
            "<info>",
            LevelClass.INFO,
            TEST_TIMESTAMP,
            "log message",
            metadata,
            cause);
    assertThat(entry.toString()).contains("<class>#<method>@<info>(INFO)");
    assertThat(entry.toString()).contains("log message");
    assertThat(entry.toString()).contains("RuntimeException");
    assertThat(entry.toString()).contains("foo=[bar]");
  }

  private static ImmutableList<Object> values(Object... values) {
    checkArgument(
        Arrays.stream(values)
            .allMatch(
                v ->
                    v instanceof Long
                        || v instanceof Double
                        || v instanceof Boolean
                        || v instanceof String),
        "Metadata values expected to only be Long, Double, Boolean or String");
    return ImmutableList.copyOf(values);
  }
}
