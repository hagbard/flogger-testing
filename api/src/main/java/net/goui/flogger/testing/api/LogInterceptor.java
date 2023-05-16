package net.goui.flogger.testing.api;

import com.google.common.collect.ImmutableList;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.goui.flogger.testing.LogEntry;

public interface LogInterceptor {

  /**
   * The support level of an interceptor implementation as determined via {@link
   * Factory#getSupportLevel()}.
   */
  enum Support {
    /**
     * This interceptor implementation extracts all possible data for creating {@code LogEntry}
     * instances for testing. Log messages contain the logged values, metadata is extracted, class
     * and method names of the call-site are correct.
     */
    FULL,

    /**
     * This interceptor implementation extracts basic information (log message) but may omit
     * additional information such as call-site information or even metadata. If an interceptor with
     * partial support is used, some logging tests are likely to start (spuriously) failing.
     *
     * <p>A partially supported log interceptor will only be used if no fully supported instances
     * can be found (and logging will alert the user to the risk of test failure).
     */
    PARTIAL,

    /**
     * This interceptor implementation is not supported and extracts little or no data from the
     * underlying log system.
     *
     * <p>The only time an interceptor with no support will be used is if there are no other options
     * and the default JDK interceptor does not work either. Logging will warn the user of likely
     * test failure and tell them to install a supported interceptor.
     */
    NONE
  }

  /**
   * Implemented by classes wishing to support log interceptors. Factory classes should register
   * themselves as service loaders for the {@link LogInterceptor} type (e.g. by using Guava's {@code
   * AutoService} annotation.
   */
  interface Factory extends Supplier<LogInterceptor> {
    /** Returns a new log interceptor for use during tests. */
    LogInterceptor get();

    /**
     * Returns the support level expected from this factory. See {@link Support} for the possible
     * levels and definitions of what each on means. If a factory falsely claims it has {@link
     * Support#FULL} support when it doesn't, tests may fail spuriously in confusing ways, and waste
     * the user's time.
     *
     * <p>The easiest way to obtain a reliable value for this is to subclass {@link
     * AbstractLogInterceptorFactory} and let it do the testing.
     */
    Support getSupportLevel();
  }

  /**
   * Attach this interceptor to an underlying logger with the given name, to capture logs at or
   * above the specified level. Only one call to attach should be made per logger name, and an
   * interceptor is permitted (but not required) to fail (throwing an {@link IllegalStateException}
   * if an attempt is made to attach the same logger name twice).
   *
   * @return a closeable "recorder" which encapsulated the attachment to the specific logger and
   *     which will be removed once testing is complete.
   */
  Recorder attachTo(String loggerName, Level level);

  /**
   * Returns a thread-safe snapshot of the current set of logs recorded across all attached loggers.
   *
   * <p>Interceptor implementations must ensure that the capturing of log entries is thread safe,
   * and that a snapshot can be taken at any time. However, in the face of concurrent logging, an
   * interceptor may choose to order logs from different threads arbitrarily (i.e. the order of logs
   * <em>need not</em> be strictly monotonic by original timestamp in the returned list, and users
   * should never rely on log entry ordering between threads).
   */
  ImmutableList<LogEntry> getLogs();

  /**
   * Detaches the interceptor from the logger for which the original {@link #attachTo(String,
   * Level)} call was made.
   */
  interface Recorder extends AutoCloseable {
    @Override
    void close();
  }
}
