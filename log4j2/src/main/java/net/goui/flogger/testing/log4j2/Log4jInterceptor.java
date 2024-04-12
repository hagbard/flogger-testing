/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.log4j2;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY;

import com.google.auto.service.AutoService;
import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.AbstractLogInterceptorFactory;
import net.goui.flogger.testing.api.DefaultFormatMetadataParser;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.MessageAndMetadata;
import net.goui.flogger.testing.api.MetadataExtractor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

/** Log interceptor for Log4J2 logging. */
public final class Log4jInterceptor implements LogInterceptor {
  @AutoService(LogInterceptor.Factory.class)
  public static final class Factory extends AbstractLogInterceptorFactory {
    @Override
    public LogInterceptor get() {
      return Log4jInterceptor.create();
    }

    @Override
    protected void configureUnderlyingLoggerForInfoLogging(String loggerName) {
      Logger underlyingLogger = (Logger) LogManager.getLogger(loggerName);
      Configurator.setLevel(underlyingLogger.getName(), Level.INFO);
      underlyingLogger.setAdditive(false);
    }
  }

  private static final MetadataExtractor<LogEvent> DEFAULT_LOG4J_EXTRACTOR =
      e -> DefaultFormatMetadataParser.parse(e.getMessage().getFormattedMessage());

  public static LogInterceptor create() {
    return create(DEFAULT_LOG4J_EXTRACTOR);
  }

  public static LogInterceptor create(MetadataExtractor<LogEvent> metadataExtractor) {
    return new Log4jInterceptor(metadataExtractor);
  }

  private final MetadataExtractor<LogEvent> metadataExtractor;

  private Log4jInterceptor(MetadataExtractor<LogEvent> metadataExtractor) {
    this.metadataExtractor = checkNotNull(metadataExtractor);
  }

  @Override
  public Recorder attachTo(String loggerName, LevelClass level, Consumer<LogEntry> collector) {
    // WARNING: Log4J is unintuitive with log level ordering (compared to JDK). A "high" level means
    // high verbosity (i.e. what most people call "low level logging").
    Level log4JLevel = toLog4JLevel(level);
    Appender appender = getAppender(collector, log4JLevel);

    // If the call to configureUnderlyingLoggerForInfoLogging() succeeded, we should be
    // able to cast the context instance.
    LoggerContext context = (LoggerContext) LogManager.getContext(false);
    // The existing config for the logger name might be a parent config.
    LoggerConfig oldConfig = context.getConfiguration().getLoggerConfig(loggerName);
    // Don't "turn off" existing logging in the logger itself (higher value == more verbose).
    Level oldLog4JLevel = oldConfig.getLevel();
    Level newLevel = Comparators.max(oldLog4JLevel, log4JLevel);
    // Add a new logger config or use the existing one.
    LoggerConfig config =
        loggerName.equals(oldConfig.getName())
            ? oldConfig
            : new LoggerConfig(loggerName, null, true);
    // Actually adds an AppenderRef
    config.addAppender(appender, null, null);
    // Sets the level of the LoggerConfig (either one).
    config.setLevel(newLevel);
    context.getConfiguration().addLogger(loggerName, config);
    context.updateLoggers();
    return () -> {
      try {
        context.getConfiguration().removeLogger(loggerName);
        context.updateLoggers();
      } catch (RuntimeException e) {
        // Ignored on close().
      }
    };
  }

  private Appender getAppender(Consumer<LogEntry> collector, Level log4JLevel) {
    LevelRangeFilter specifiedLevelAndAbove =
        LevelRangeFilter.createFilter(
            /* minLevel (null ==> max) */ null,
            /* maxLevel */ log4JLevel,
            /* onMatch (null ==> accept) */ Result.NEUTRAL,
            /* onMismatch (null ==> deny) */ Result.DENY);
    return new AbstractAppender(
        "CapturingAppender",
        specifiedLevelAndAbove,
        /* layout */ null,
        /* ignoreExceptions */ true,
        EMPTY_ARRAY) {
      @Override
      public void append(LogEvent event) {
        collector.accept(toLogEntry(event, metadataExtractor.extract(event)));
      }
    };
  }

  private LogEntry toLogEntry(LogEvent event, MessageAndMetadata mm) {
    StackTraceElement source = event.getSource(); // nullable
    Level log4jLevel = event.getLevel();
    var ts = event.getInstant();
    Instant timestamp = Instant.ofEpochSecond(ts.getEpochSecond(), ts.getNanoOfSecond());
    ImmutableMap<String, ImmutableList<Object>> metadata = mergeMdcValues(event, mm.metadata());
    return LogEntry.of(
        source != null ? source.getClassName() : null,
        source != null ? source.getMethodName() : null,
        log4jLevel.name(),
        toLevelClass(log4jLevel),
        timestamp,
        event.getThreadId(),
        mm.message(),
        metadata,
        event.getThrown());
  }

  private static ImmutableMap<String, ImmutableList<Object>> mergeMdcValues(
      LogEvent event, ImmutableMap<String, ImmutableList<Object>> metadata) {
    ReadOnlyStringMap mdc = event.getContextData();
    if (!mdc.isEmpty()) {
      LinkedHashMap<String, ImmutableList<Object>> withMdc = new LinkedHashMap<>(metadata);
      mdc.forEach(
          (k, v, b) -> {
            Object safeValue = toSafeValue(v);
            if (safeValue != null) {
              ImmutableList<Object> values = withMdc.get(k);
              if (values != null) {
                // Since the MDC is NOT a multimap, each key only appears at most once, so we never
                // see repeated resizing of the values list.
                ArrayList<Object> appended = new ArrayList<>(values.size() + 1);
                appended.add(safeValue);
                withMdc.put(k, ImmutableList.copyOf(appended));
              } else {
                withMdc.put(k, ImmutableList.of(safeValue));
              }
            }
          },
          withMdc);
      metadata = ImmutableMap.copyOf(withMdc);
    }
    return metadata;
  }

  // Convert arbitrary values into metadata compatible types (Double, Long, Boolean, String) to
  // avoid carrying large/mutable instances into test logic.
  private static Object toSafeValue(Object v) {
    if (v instanceof Double || v instanceof Float || v instanceof BigDecimal) {
      return ((Number) v).doubleValue();
    }
    if (v instanceof Number) {
      return ((Number) v).longValue();
    }
    if (v instanceof Boolean) {
      return v;
    }
    try {
      return v.toString();
    } catch (RuntimeException e) {
      return null;
    }
  }

  // WARNING: Log4J level values *decrease* with severity.
  private static LevelClass toLevelClass(Level level) {
    if (level.compareTo(Level.ERROR) <= 0) return LevelClass.SEVERE;
    if (level.compareTo(Level.WARN) <= 0) return LevelClass.WARNING;
    if (level.compareTo(Level.INFO) <= 0) return LevelClass.INFO;
    if (level.compareTo(Level.DEBUG) <= 0) return LevelClass.FINE;
    return LevelClass.FINEST;
  }

  private static Level toLog4JLevel(LevelClass level) {
    switch (level) {
      case FINEST:
        return Level.TRACE;
      case FINE:
        return Level.DEBUG;
      case INFO:
        return Level.INFO;
      case WARNING:
        return Level.WARN;
      case SEVERE:
        return Level.ERROR;
      default:
        throw new AssertionError("unknown level: " + level);
    }
  }
}
