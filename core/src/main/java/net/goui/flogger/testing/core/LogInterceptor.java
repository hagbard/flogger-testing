package net.goui.flogger.testing.core;

import com.google.common.collect.ImmutableList;

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
