package net.goui.flogger.testing.log4j2;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY;

import com.google.common.collect.ImmutableList;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.core.DefaultFormatMetadataParser;
import net.goui.flogger.testing.core.LogEntry;
import net.goui.flogger.testing.core.LogInterceptor;
import net.goui.flogger.testing.core.MessageAndMetadata;
import net.goui.flogger.testing.core.MetadataExtractor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;

public final class Log4jInterceptor implements LogInterceptor {
  private static final int JDK_SEVERE_VALUE = java.util.logging.Level.SEVERE.intValue();
  private static final int JDK_WARNING_VALUE = java.util.logging.Level.WARNING.intValue();
  private static final int JDK_INFO_VALUE = java.util.logging.Level.INFO.intValue();
  private static final int JDK_FINE_VALUE = java.util.logging.Level.FINE.intValue();

  private static final MetadataExtractor<LogEvent> DEFAULT_LOG4J_EXTRACTOR =
      e -> DefaultFormatMetadataParser.parse(e.getMessage().getFormattedMessage());

  public static LogInterceptor create() {
    return create(DEFAULT_LOG4J_EXTRACTOR);
  }

  public static LogInterceptor create(MetadataExtractor<LogEvent> metadataExtractor) {
    return new Log4jInterceptor(metadataExtractor);
  }

  private final ConcurrentLinkedQueue<LogEntry> records = new ConcurrentLinkedQueue<>();
  private final MetadataExtractor<LogEvent> metadataExtractor;

  private Log4jInterceptor(MetadataExtractor<LogEvent> metadataExtractor) {
    this.metadataExtractor = checkNotNull(metadataExtractor);
  }

  @Override
  public Recorder attachTo(String loggerName, java.util.logging.Level jdkLevel) {
    // WARNING: Log4J is unintuitive with log level ordering (compared to JDK). A "high" level means
    // high verbosity (i.e. what most people call "low level logging").
    LevelRangeFilter specifiedLevelAndAbove =
        LevelRangeFilter.createFilter(
            /* minLevel (null ==> max) */ null,
            /* maxLevel */ toLog4JLevel(jdkLevel),
            /* onMatch (null ==> accept) */ Result.NEUTRAL,
            /* onMismatch (null ==> deny) */ Result.DENY);

    Appender appender =
        new AbstractAppender(
            "CapturingAppender",
            specifiedLevelAndAbove,
            /* layout */ null,
            /* ignoreExceptions */ true,
            EMPTY_ARRAY) {
          @Override
          public void append(LogEvent event) {
            records.add(toLogEntry(event));
          }
        };

    Logger logger = (Logger) LogManager.getLogger(loggerName);
    // WARNING: If the logger referenced here has had transient modifications made to it (e.g.
    // calling setLevel() directly rather than using configuration to set the level), the act of
    // adding an appender here will reset the logger to its "original state". This is almost
    // impossible to avoid unfortunately, but with appropriate warnings in the right places it
    // shouldn't be too bad. The "Configurator" class is a way to set a logger's level
    // programmatically which won't be undone here.
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

  private LogEntry toLogEntry(LogEvent event) {
    StackTraceElement source = event.getSource(); // nullable
    String className = source != null ? source.getClassName() : null;
    String methodName = source != null ? source.getMethodName() : null;
    Level log4jLevel = event.getLevel();
    MessageAndMetadata mm = metadataExtractor.extract(event);
    return LogEntry.of(
        className,
        methodName,
        log4jLevel.name(),
        toLevelClass(log4jLevel),
        mm.message(),
        mm.metadata(),
        event.getThrown());
  }

  // WARNING: Log4J level values *decrease* with severity.
  private static LevelClass toLevelClass(Level level) {
    if (level.compareTo(Level.ERROR) <= 0) return LevelClass.SEVERE;
    if (level.compareTo(Level.WARN) <= 0) return LevelClass.WARNING;
    if (level.compareTo(Level.INFO) <= 0) return LevelClass.INFO;
    if (level.compareTo(Level.DEBUG) <= 0) return LevelClass.FINE;
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
