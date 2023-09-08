/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

This program and the accompanying materials are made available under the terms of the
Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.api;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.goui.flogger.testing.LogEntry;

/**
 * Defines an interceptor which can extract log entries from one or more underlying loggers for use
 * in test assertions.
 *
 * <p>While users can supply interceptors directly to the test API, implementations of this
 * interface should also be made available from a {@link LogInterceptor.Factory} class which is
 * registered as a "service" (i.e. obtained via {@code ServiceLoader.load(Factory.class)}).
 */
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
     * partial support is used, some logging tests are likely to start failing spuriously.
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
   * themselves as "services" (e.g. by using Guava's {@code AutoService} annotation.
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
  Recorder attachTo(String loggerName, Level level, Consumer<LogEntry> collector, String testId);

  static boolean shouldCollect(MessageAndMetadata mm, String testId) {
    return TestingApi.hasMatchingTestId(mm, testId);
  }

  /**
   * Detaches the interceptor from the logger for which the original {@link #attachTo(String, Level,
   * Consumer, String)} call was made.
   */
  interface Recorder extends AutoCloseable {
    @Override
    void close();
  }
}
