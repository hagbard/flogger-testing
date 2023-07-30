package net.goui.flogger.testing.jdk;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.AbstractLogInterceptorFactory;
import net.goui.flogger.testing.api.DefaultFormatMetadataParser;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.MessageAndMetadata;
import net.goui.flogger.testing.api.MetadataExtractor;

public final class JdkInterceptor implements LogInterceptor {

  public static class Factory extends AbstractLogInterceptorFactory {
    @Override
    public LogInterceptor get() {
      return JdkInterceptor.create();
    }

    @Override
    protected void configureUnderlyingLoggerForInfoLogging(String loggerName) {
      Logger underlyingLogger = Logger.getLogger(loggerName);
      underlyingLogger.setLevel(Level.INFO);
      underlyingLogger.setUseParentHandlers(false);
    }
  }

  private final MetadataExtractor<String> metadataParser;

  public static LogInterceptor create() {
    return new JdkInterceptor(DefaultFormatMetadataParser::parse);
  }

  private JdkInterceptor(MetadataExtractor<String> metadataParser) {
    this.metadataParser = checkNotNull(metadataParser);
  }

  @Override
  public Recorder attachTo(
      String loggerName, Level level, Consumer<LogEntry> collector, String testId) {
    CapturingHandler handler = new CapturingHandler(collector, testId);
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

  private class CapturingHandler extends Handler {
    private final Consumer<LogEntry> collector;
    private final String testId;

    private CapturingHandler(Consumer<LogEntry> collector, String testId) {
      this.collector = collector;
      this.testId = testId;
    }

    @Override
    public void publish(LogRecord record) {
      Level level = record.getLevel();
      MessageAndMetadata mm = metadataParser.extract(record.getMessage());
      if (LogInterceptor.shouldCollect(mm, testId)) {
        collector.accept(
            LogEntry.of(
                record.getSourceClassName(),
                record.getSourceMethodName(),
                level.getName(),
                levelClassOf(level),
                mm.message(),
                mm.metadata(),
                record.getThrown()));
      }
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
