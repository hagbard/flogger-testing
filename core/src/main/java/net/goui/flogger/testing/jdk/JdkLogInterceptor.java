package net.goui.flogger.testing.jdk;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.goui.flogger.testing.core.DefaultMetadataExtractor;
import net.goui.flogger.testing.core.LogInterceptor;
import net.goui.flogger.testing.core.MessageAndMetadata;
import net.goui.flogger.testing.core.MetadataExtractor;
import net.goui.flogger.testing.core.truth.LogEntry;

public final class JdkLogInterceptor implements LogInterceptor {
  private final ConcurrentLinkedQueue<LogEntry> logs = new ConcurrentLinkedQueue<>();
  private final MetadataExtractor metadataExtractor;

  public static LogInterceptor create() {
    return new JdkLogInterceptor(new DefaultMetadataExtractor());
  }

  private JdkLogInterceptor(MetadataExtractor metadataExtractor) {
    this.metadataExtractor = checkNotNull(metadataExtractor);
  }

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
      MessageAndMetadata mm = metadataExtractor.parse(record.getMessage());
      Level level = record.getLevel();
      logs.add(
          LogEntry.of(
              l -> Integer.compare(level.intValue(), l.intValue()),
              level.getName(),
              mm.message(),
              mm.metadata(),
              record.getThrown()));
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
  }
}
