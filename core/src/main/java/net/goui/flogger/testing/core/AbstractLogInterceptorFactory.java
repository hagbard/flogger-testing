package net.goui.flogger.testing.core;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.context.Tags;
import net.goui.flogger.testing.core.LogInterceptor.Support;

import java.util.logging.Level;

import static com.google.common.flogger.LogContext.Key.TAGS;

public abstract class AbstractLogInterceptorFactory implements LogInterceptor.Factory {
  private static final FluentLogger testFluentLogger = FluentLogger.forEnclosingClass();
  private static final String EXPECTED_LOGGER_NAME =
      AbstractLogInterceptorFactory.class.getCanonicalName();

  protected abstract void configureUnderlyingLoggerForFinestLogging(String loggerName);

  @Override
  public final Support getSupportLevel() {
    configureUnderlyingLoggerForFinestLogging(EXPECTED_LOGGER_NAME);
    LogInterceptor interceptor = get();
    try (LogInterceptor.Recorder r = interceptor.attachTo(EXPECTED_LOGGER_NAME, Level.FINEST)) {
      testFluentLogger.atFinest().with(TAGS, Tags.of("foo", "bar")).log("Test message");
    }
    Support support = Support.NONE;
    if (!interceptor.getLogs().isEmpty()) {
      LogEntry logEntry = interceptor.getLogs().get(0);
      // Don't test equality here (a custom formatter could modify the final message).
      if (logEntry.getMessage().contains("Test message")) {
        // See if metadata correctly extracted.
        support =
            ImmutableList.of("bar").equals(logEntry.getMetadata().get("foo"))
                ? Support.FULL
                : Support.PARTIAL;
      }
    }
    return support;
  }
}
