package net.goui.flogger.testing.truth;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.regex.Pattern;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import org.checkerframework.checker.nullness.qual.Nullable;

public class LogSubject extends Subject implements LogAssertion {
  private final LogEntry logEntry;

  protected LogSubject(FailureMetadata metadata, @Nullable LogEntry logEntry) {
    super(metadata, logEntry);
    this.logEntry = logEntry;
  }

  private LogEntry entry() {
    return checkNotNull(logEntry);
  }

  public static Factory<LogSubject, LogEntry> logEntries() {
    return LogSubject::new;
  }

  public static LogSubject assertThat(LogEntry logEntry) {
    return assertAbout(logEntries()).that(logEntry);
  }

  @Override
  public void contains(String substring) {
    if (!entry().message().contains(substring)) {
      failWithActual("expected to contain substring", substring);
    }
  }

  @Override
  public void containsMatch(String regex) {
    Pattern pattern = Pattern.compile(regex);
    check("message()").that(entry().message()).containsMatch(regex);
  }

  @Override
  public void hasMetadata(String key, boolean value) {
    metadataContainsImpl(key, value);
  }

  @Override
  public void hasMetadata(String key, long value) {
    metadataContainsImpl(key, value);
  }

  @Override
  public void hasMetadata(String key, double value) {
    metadataContainsImpl(key, value);
  }

  @Override
  public void hasMetadata(String key, @Nullable String value) {
    metadataContainsImpl(key, value);
  }

  private void metadataContainsImpl(String key, @Nullable Object value) {
    check("metadata()")
        .withMessage("log metadata did not contain key %s", key)
        .that(entry().metadata())
        .containsKey(key);
    if (value != null) {
      ImmutableList<Object> values = checkNotNull(entry().metadata().get(key));
      String valueStr = value instanceof String ? "\"" + values + "\"" : value.toString();
      check("metadata().get(\"%s\")", key)
          .withMessage("log metadata did not contain entry {%s: %s}", key, valueStr)
          .that(values)
          .contains(value);
    }
  }

  @Override
  public void hasCause(Class<? extends Throwable> type) {
    check("cause()").that(entry().cause()).isInstanceOf(type);
  }

  @Override
  public void isAtLevel(LevelClass level) {
    check("level()").that(entry().levelClass()).isEqualTo(level);
  }
}
