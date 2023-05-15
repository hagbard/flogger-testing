package net.goui.flogger.testing.api;

import com.google.common.collect.ImmutableList;
import net.goui.flogger.testing.LogEntry;

import java.util.function.Supplier;
import java.util.logging.Level;

public interface LogInterceptor {
  enum Support {
    FULL,
    PARTIAL,
    NONE
  }

  interface Factory extends Supplier<LogInterceptor> {
    LogInterceptor get();
    Support getSupportLevel();
  }

  Recorder attachTo(String loggerName, Level level);

  ImmutableList<LogEntry> getLogs();

  interface Recorder extends AutoCloseable {
    @Override
    void close();
  }
}
