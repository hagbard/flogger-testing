package net.goui.flogger.testing.core.test;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class LogEntry {
  abstract String message();

  LogEntry of(String message) {
    return new AutoValue_LogEntry(message);
  }
}
