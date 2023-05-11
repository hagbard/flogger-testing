package net.goui.flogger.testing.log4j2;

import static org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.goui.flogger.testing.core.LogEntry;
import net.goui.flogger.testing.core.LogEntry.LevelClass;
import net.goui.flogger.testing.core.LogInterceptor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.filter.LevelMatchFilter;

public final class Log4jInterceptor implements LogInterceptor {
  private static final int JDK_SEVERE_VALUE = java.util.logging.Level.SEVERE.intValue();
  private static final int JDK_WARNING_VALUE = java.util.logging.Level.WARNING.intValue();
  private static final int JDK_INFO_VALUE = java.util.logging.Level.INFO.intValue();
  private static final int JDK_FINE_VALUE = java.util.logging.Level.FINE.intValue();

  ConcurrentLinkedQueue<LogEntry> records = new ConcurrentLinkedQueue<>();

  @Override
  public Recorder attachTo(String loggerName, java.util.logging.Level jdkLevel) {
    var filter = LevelMatchFilter.newBuilder().setLevel(toLog4JLevel(jdkLevel)).build();
    Appender appender =
        new AbstractAppender(
            "CapturingAppender",
            filter,
            /* layout */ null,
            /* ignoreExceptions */ true,
            EMPTY_ARRAY) {
          @Override
          public void append(LogEvent event) {
            records.add(toLogEntry(event));
          }
        };
    Logger logger = (Logger) LogManager.getLogger(loggerName);
    logger.addAppender(appender);
    return () -> {
      try {
        logger.removeAppender(appender);
      } catch (RuntimeException e) {
        // Ignored on close().
      }
    };
  }

  @Override
  public ImmutableList<LogEntry> getLogs() {
    return ImmutableList.copyOf(records);
  }

  private static LogEntry toLogEntry(LogEvent event) {
    StackTraceElement source = event.getSource(); // nullable
    String className = source != null ? source.getClassName() : null;
    String methodName = source != null ? source.getMethodName() : null;
    Level log4jLevel = event.getLevel();
    String message = event.getMessage().getFormattedMessage();
    return LogEntry.of(
        className,
        methodName,
        log4jLevel.name(),
        toLevelClass(log4jLevel),
        message,
        ImmutableMap.of(),
        event.getThrown());
  }

  private static LevelClass toLevelClass(Level level) {
    if (level.compareTo(Level.ERROR) >= 0) return LevelClass.SEVERE;
    if (level.compareTo(Level.WARN) >= 0) return LevelClass.WARNING;
    if (level.compareTo(Level.INFO) >= 0) return LevelClass.INFO;
    if (level.compareTo(Level.DEBUG) >= 0) return LevelClass.FINE;
    return LevelClass.FINEST;
  }

  private static org.apache.logging.log4j.Level toLog4JLevel(java.util.logging.Level level) {
    int jdkLevelValue = level.intValue();
    if (jdkLevelValue >= JDK_SEVERE_VALUE) return Level.ERROR;
    if (jdkLevelValue >= JDK_WARNING_VALUE) return Level.WARN;
    if (jdkLevelValue >= JDK_INFO_VALUE) return Level.INFO;
    if (jdkLevelValue >= JDK_FINE_VALUE) return Level.DEBUG;
    return org.apache.logging.log4j.Level.TRACE;
  }
}