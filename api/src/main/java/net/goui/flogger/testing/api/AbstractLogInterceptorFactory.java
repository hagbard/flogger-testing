package net.goui.flogger.testing.api;

import static java.lang.Boolean.TRUE;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import java.util.Objects;
import java.util.logging.Level;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.LogInterceptor.Support;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An implementation of {@link LogInterceptor.Factory} which supplies its own test to the determine
 * support level of an interceptor. Subclasses need only configure an underlying logger instance
 * programmatically in order to get a good measure of support.
 */
public abstract class AbstractLogInterceptorFactory implements LogInterceptor.Factory {
  private static final FluentLogger testFluentLogger = FluentLogger.forEnclosingClass();
  private static final String EXPECTED_LOGGER_NAME =
      AbstractLogInterceptorFactory.class.getCanonicalName();

  protected abstract void configureUnderlyingLoggerForInfoLogging(String loggerName);

  /**
   * A fairly comprehensive test to determine the support level for an interceptor using {@link
   * FluentLogger} to sample the behaviour of the subclass interceptor implementation.
   *
   * @return the support level of the implementation subclass.
   */
  @Override
  public final Support getSupportLevel() {
    configureUnderlyingLoggerForInfoLogging(EXPECTED_LOGGER_NAME);
    LogInterceptor interceptor = get();
    RuntimeException testCause = new RuntimeException();
    try (LogInterceptor.Recorder r = interceptor.attachTo(EXPECTED_LOGGER_NAME, Level.FINE)) {
      testFluentLogger.atInfo().withCause(testCause).log("<<enabled message>>");
      testFluentLogger.atFine().log("<<forced message>>");
      testFluentLogger.atFinest().log("<<disabled log>>");
    }
    ImmutableList<LogEntry> logged = interceptor.getLogs();
    if (logged.isEmpty()) {
      return Support.NONE;
    }
    // Support can only go down as checks fail.
    Support support = Support.FULL;
    LogEntry enabledLog = logged.get(0);
    support =
        min(
            support,
            testBasicSupport(enabledLog, LevelClass.INFO, "<<enabled message>>", testCause));
    if (logged.size() == 2) {
      LogEntry forcedLog = logged.get(1);
      support =
          min(support, testBasicSupport(forcedLog, LevelClass.FINEST, "<<forced message>>", null));
      // As well as basic support, test for the expected "forced=true" metadata.
      ImmutableList<Object> values = forcedLog.metadata().get("forced");
      if (values == null || !values.contains(TRUE)) {
        support = min(support, Support.PARTIAL);
      }
    } else {
      support = min(support, Support.PARTIAL);
    }
    return support;
  }

  /** Returns the minimum of the given support levels. */
  private static Support min(Support existing, Support result) {
    return existing.compareTo(result) < 0 ? existing : result;
  }

  private static Support testBasicSupport(
      LogEntry e, LevelClass levelClass, String messageSubstring, @Nullable Throwable cause) {
    if (!e.message().contains(messageSubstring)) {
      return Support.NONE;
    }
    boolean fullSupport =
        e.className().equals(EXPECTED_LOGGER_NAME)
            && e.methodName().equals("getSupportLevel")
            && e.levelClass() == levelClass
            && Objects.equals(e.cause(), cause);
    return fullSupport ? Support.FULL : Support.PARTIAL;
  }
}
