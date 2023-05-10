package net.goui.flogger.testing.core.truth;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.goui.flogger.testing.core.truth.LogEntry.LevelCheck;
import org.checkerframework.checker.nullness.qual.Nullable;

public class LogEntrySubject extends Subject implements LogAssertion {
  private final LogEntry logEntry;

  protected LogEntrySubject(FailureMetadata metadata, @Nullable LogEntry logEntry) {
    super(metadata, logEntry);
    this.logEntry = logEntry;
  }

  private LogEntry entry() {
    return checkNotNull(logEntry);
  }

  public static Factory<LogEntrySubject, LogEntry> logEntries() {
    return LogEntrySubject::new;
  }

  public static LogEntrySubject assertThat(LogEntry logEntry) {
    return assertAbout(logEntries()).that(logEntry);
  }

  @Override
  public void messageContains(String substring) {
    if (!entry().getMessage().contains(substring)) {
      failWithActual("expected log message to contain substring", substring);
    }
  }

  @Override
  public void messageMatches(String regex) {
    Pattern pattern = Pattern.compile(regex);
    if (!pattern.matcher(entry().getMessage()).find()) {
      failWithActual("expected log message to match regular expression", regex);
    }
  }

  @Override
  public void metadataContains(String key, boolean value) {
    metadataContainsImpl(key, value);
  }

  @Override
  public void metadataContains(String key, long value) {
    metadataContainsImpl(key, value);
  }

  @Override
  public void metadataContains(String key, double value) {
    metadataContainsImpl(key, value);
  }

  @Override
  public void metadataContains(String key, String value) {
    metadataContainsImpl(key, value);
  }

  private void metadataContainsImpl(String key, Object value) {
    ImmutableList<Object> values = entry().getMetadata().getOrDefault(key, ImmutableList.of());
    check("metadata[%s]", key)
        .withMessage("expected metadata to contain '%s'='%s'", key, value)
        .that(values)
        .contains(value);
  }

  @Override
  public void hasCause(Class<? extends Throwable> type) {
    check("cause()")
        .withMessage("expected cause to be of type: %s", type)
        .that(entry().getCause())
        .isInstanceOf(type);
  }

  @Override
  public void levelIsCompatibleWith(Level level) {
    if (entry().checkLevel(level) != LevelCheck.COMPATIBLE) {
      failWithoutActual(
          Fact.fact(
              "expected logged level '" + entry().levelName() + "' to be compatible with", level));
    }
  }

  @Override
  public void levelIsAbove(Level level) {
    if (entry().checkLevel(level) != LevelCheck.ABOVE) {
      failWithoutActual(
          Fact.fact("expected logged level '" + entry().levelName() + "' to be above", level));
    }
  }

  @Override
  public void levelIsBelow(Level level) {
    if (entry().checkLevel(level) != LevelCheck.BELOW) {
      failWithoutActual(
          Fact.fact("expected logged level '" + entry().levelName() + "' to be below", level));
    }
  }
}
