package net.goui.flogger.testing.jdk;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.core.*;

public final class JdkInterceptor implements LogInterceptor {

  public static class Factory extends AbstractLogInterceptorFactory {
    @Override
    public LogInterceptor get() {
      return JdkInterceptor.create();
    }

    @Override
    protected void configureUnderlyingLoggerForFinestLogging(String loggerName) {
      Logger underlyingLogger = Logger.getLogger(loggerName);
      underlyingLogger.setLevel(Level.FINEST);
      underlyingLogger.setUseParentHandlers(false);
    }
  }

  private final ConcurrentLinkedQueue<LogEntry> logs = new ConcurrentLinkedQueue<>();
  private ImmutableList<LogEntry> logsSnapshot = ImmutableList.of();
  private final MetadataExtractor<String> metadataParser;

  public static LogInterceptor create() {
    return new JdkInterceptor(DefaultFormatMetadataParser::parse);
  }

  private JdkInterceptor(MetadataExtractor<String> metadataParser) {
    this.metadataParser = checkNotNull(metadataParser);
  }

  @Override
  public Recorder attachTo(String loggerName, Level level) {
    CapturingHandler handler = new CapturingHandler();
    handler.setLevel(level);
    Logger jdkLogger = Logger.getLogger(loggerName);
    jdkLogger.addHandler(handler);
    return () -> {
      try {
        jdkLogger.removeHandler(handler);
      } catch (RuntimeException e) {
        // Ignored on close().
      }
    };
  }

  @Override
  public ImmutableList<LogEntry> getLogs() {
    // Not thread safe, but asserting should not be concurrent with logging.
    if (logsSnapshot.size() != logs.size()) {
      logsSnapshot = ImmutableList.copyOf(logs);
    }
    return logsSnapshot;
  }

  private class CapturingHandler extends Handler {
    @Override
    public void publish(LogRecord record) {
      MessageAndMetadata mm = metadataParser.extract(record.getMessage());
      Level level = record.getLevel();
      logs.add(
          LogEntry.of(
              record.getSourceClassName(),
              record.getSourceMethodName(),
              level.getName(),
              levelClassOf(level),
              mm.message(),
              mm.metadata(),
              record.getThrown()));
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
  }

  private static final int SEVERE_VALUE = Level.SEVERE.intValue();
  private static final int WARNING_VALUE = Level.WARNING.intValue();
  private static final int INFO_VALUE = Level.INFO.intValue();
  private static final int FINE_VALUE = Level.FINE.intValue();

  private static LevelClass levelClassOf(Level level) {
    int levelValue = level.intValue();
    if (levelValue >= SEVERE_VALUE) return LevelClass.SEVERE;
    if (levelValue >= WARNING_VALUE) return LevelClass.WARNING;
    if (levelValue >= INFO_VALUE) return LevelClass.INFO;
    if (levelValue >= FINE_VALUE) return LevelClass.FINE;
    return LevelClass.FINEST;
  }
}
