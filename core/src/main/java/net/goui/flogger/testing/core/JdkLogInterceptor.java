package net.goui.flogger.testing.core;

import com.google.common.collect.ImmutableList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.goui.flogger.testing.core.truth.LogEntry;

public class JdkLogInterceptor implements LogInterceptor {
  private final ConcurrentLinkedQueue<LogEntry> logs = new ConcurrentLinkedQueue<>();

  @Override
  public Recorder attachTo(String loggerName, Level level) {
    CapturingHandler handler = new CapturingHandler();
    handler.setLevel(level);
    Logger logger = Logger.getLogger(loggerName);
    logger.addHandler(handler);
    return () -> logger.removeHandler(handler);
  }

  @Override
  public ImmutableList<LogEntry> getLogs() {
    return ImmutableList.copyOf(logs);
  }

  private class CapturingHandler extends Handler {
    @Override
    public void publish(LogRecord record) {
      Level level = record.getLevel();
      logs.add(
          LogEntry.of(
              l -> Integer.compare(level.intValue(), l.intValue()),
              level.getName(),
              record.getMessage()));
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
  }
}
