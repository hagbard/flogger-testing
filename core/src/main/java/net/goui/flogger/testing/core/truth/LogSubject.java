package net.goui.flogger.testing.core.truth;

import static com.google.common.truth.Truth.assertAbout;
import static net.goui.flogger.testing.core.truth.LogEntrySubject.logEntries;
import static net.goui.flogger.testing.core.truth.ScopedLogSubject.scopedLogs;

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

  public ScopedLogSubject everyLog() {
    return check("everyLog()").about(scopedLogs()).that(ScopedLog.everyMatch(log));
  }

  public ScopedLogSubject noLog() {
    return check("noLog()").about(scopedLogs()).that(ScopedLog.noMatch(log));
  }

  public ScopedLogSubject anyLog() {
    return check("anyLog()").about(scopedLogs()).that(ScopedLog.anyMatch(log));
  }

  public LogEntrySubject log(int n) {
    return check("log(%s)", n).about(logEntries()).that(log.get(n));
  }
}
