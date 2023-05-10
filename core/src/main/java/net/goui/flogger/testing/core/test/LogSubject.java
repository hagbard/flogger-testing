package net.goui.flogger.testing.core.test;

import static com.google.common.truth.Truth.assertAbout;
import static net.goui.flogger.testing.core.test.LogEntrySubject.logEntries;
import static net.goui.flogger.testing.core.test.ScopedLogSubject.scopedLogs;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

public class LogSubject extends Subject {
  private final ImmutableList<LogEntry> log;

  protected LogSubject(FailureMetadata metadata, ImmutableList<LogEntry> log) {
    super(metadata, log);
    this.log = log;
  }

  public static Factory<LogSubject, ImmutableList<LogEntry>> logs() {
    return LogSubject::new;
  }

  public static LogSubject assertThat(ImmutableList<LogEntry> log) {
    return assertAbout(logs()).that(log);
  }

  public ScopedLogSubject everyEntry() {
    return check("<every>").about(scopedLogs()).that(ScopedLog.every(log));
  }

  public LogEntrySubject entry(int n) {
    return check("entry(%s)", n).about(logEntries()).that(log.get(n));
  }
}
