package net.goui.flogger.testing.core;

import com.google.common.collect.ImmutableList;
import java.util.logging.Level;
import net.goui.flogger.testing.core.truth.LogEntry;

public interface LogInterceptor {
  Recorder attachTo(String loggerName, Level level);

  ImmutableList<LogEntry> getLogs();



  interface Recorder extends AutoCloseable {
    @Override
    void close();
  }
}
