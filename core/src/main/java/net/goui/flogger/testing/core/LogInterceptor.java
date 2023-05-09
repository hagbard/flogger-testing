package net.goui.flogger.testing.core;

import com.google.common.collect.ImmutableList;
import java.util.logging.Level;

public interface LogInterceptor {
  Recorder attachTo(String loggerName, Level level);

  ImmutableList<CapturedLog> getLogs();



  interface Recorder extends AutoCloseable {
    @Override
    void close();
  }
}
