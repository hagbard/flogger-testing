package net.goui.flogger.testing.truth;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import net.goui.flogger.testing.LogEntry;

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

  public IntegerSubject logCount() {
    return check("size()").that(log.size());
  }

  public ScopedLogSubject everyLog() {
    return check("everyLog()").about(ScopedLogSubject.scopedLogs()).that(ScopedLog.everyMatch(log));
  }

  public ScopedLogSubject noLog() {
    return check("noLog()").about(ScopedLogSubject.scopedLogs()).that(ScopedLog.noMatch(log));
  }

  public ScopedLogSubject anyLog() {
    return check("anyLog()").about(ScopedLogSubject.scopedLogs()).that(ScopedLog.anyMatch(log));
  }
}
