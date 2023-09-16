/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

 This program and the accompanying materials are made available under the terms of the
 Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

 SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.jdk;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.flogger.backend.system.AbstractLogRecord;
import java.time.Instant;
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

/** Log interceptor for JDK logging. */
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
      String loggerName, LevelClass level, Consumer<LogEntry> collector, String testId) {
    CapturingHandler handler = new CapturingHandler(collector, testId);
    handler.setLevel(level.toJdkLogLevel());
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

    @SuppressWarnings("deprecation")
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
                getBestTimestamp(record),
                // Cannot use "getLongThreadId()" until supported JDK bumped to 16.
                record.getThreadID(),
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

  private static Instant getBestTimestamp(LogRecord record) {
    Instant bestTimestamp;
    if (record instanceof AbstractLogRecord) {
      long timestampNanos = ((AbstractLogRecord) record).getLogData().getTimestampNanos();
      long seconds = NANOSECONDS.toSeconds(timestampNanos);
      bestTimestamp = Instant.ofEpochSecond(seconds, timestampNanos - SECONDS.toNanos(seconds));
    } else {
      long timestampMillis = record.getMillis();
      long seconds = MILLISECONDS.toSeconds(timestampMillis);
      long millis = timestampMillis - SECONDS.toMillis(seconds);
      bestTimestamp = Instant.ofEpochSecond(seconds, MILLISECONDS.toNanos(millis));
    }
    return bestTimestamp;
  }
}
