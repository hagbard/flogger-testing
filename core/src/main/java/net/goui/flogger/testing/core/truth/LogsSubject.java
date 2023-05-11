package net.goui.flogger.testing.core.truth;

import static com.google.common.truth.Truth.assertAbout;
import static net.goui.flogger.testing.core.truth.LogSubject.logEntries;
import static net.goui.flogger.testing.core.truth.ScopedLogSubject.scopedLogs;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import net.goui.flogger.testing.core.LogEntry;

public class LogsSubject extends Subject {
  private final ImmutableList<LogEntry> log;

  protected LogsSubject(FailureMetadata metadata, ImmutableList<LogEntry> log) {
    super(metadata, log);
    this.log = log;
  }

  public static Factory<LogsSubject, ImmutableList<LogEntry>> logs() {
    return LogsSubject::new;
  }

  public static LogsSubject assertThat(ImmutableList<LogEntry> log) {
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

  public LogSubject get(int n) {
    return assertAbout(logEntries()).that(log.get(n));
  }
}
