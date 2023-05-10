package net.goui.flogger.testing.core.test;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import org.checkerframework.checker.nullness.qual.Nullable;

public class LogEntrySubject extends Subject implements LogAssertion {
  private final LogEntry logEntry;

  protected LogEntrySubject(FailureMetadata metadata, @Nullable LogEntry logEntry) {
    super(metadata, logEntry);
    this.logEntry = logEntry;
  }

  public static Factory<LogEntrySubject, LogEntry> logEntries() {
    return LogEntrySubject::new;
  }

  public static LogEntrySubject assertThat(LogEntry logEntry) {
    return assertAbout(logEntries()).that(logEntry);
  }

  @Override
  public void messageContains(String substring) {
    if (!checkNotNull(logEntry).message().contains(substring)) {
      failWithActual("expected log message to contain", substring);
    }
  }
}
