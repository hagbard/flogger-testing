package net.goui.flogger.log4j2;

import static com.google.common.flogger.LogContext.Key.TAGS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.logging.Level.INFO;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.context.Tags;
import net.goui.flogger.testing.core.LogEntry;
import net.goui.flogger.testing.core.LogInterceptor;
import net.goui.flogger.testing.core.LogInterceptor.Recorder;
import net.goui.flogger.testing.log4j2.Log4jInterceptor;
import net.goui.flogger.testing.truth.LogSubject;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Log4jInterceptorTest {
  Logger logger(String name) {
    Logger logger = (Logger) LogManager.getLogger(name);
    // DO NOT use `logger.setLevel()` in tests because the state it sets is undone when the appender
    // is added. We assume that in testing users are not using loggers with transient state set.
    Configurator.setLevel(logger, Level.TRACE);
    return logger;
  }

  @Test
  public void testInterceptorScope_logNames() {
    Logger logger = logger("foo.bar.Baz");
    Logger childLogger = logger("foo.bar.Baz.Child");
    Logger parentLogger = logger("foo.bar");
    Logger siblingLogger = logger("foo.bar.Sibling");

    LogInterceptor interceptor = Log4jInterceptor.create();
    ImmutableList<LogEntry> logged;
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", INFO)) {

      assertThat(interceptor.getLogs()).isEmpty();

      logger.info("Log message");
      childLogger.info("Child message");
      parentLogger.info("Parent message");
      siblingLogger.info("Sibling message");

      logged = interceptor.getLogs();
      assertThat(logged).hasSize(2);
      assertThat(logged.get(0).getMessage()).isEqualTo("Log message");
      assertThat(logged.get(1).getMessage()).isEqualTo("Child message");
    }
    logger.error("Should not be captured!!");
    assertThat(interceptor.getLogs()).isEqualTo(logged);
  }

  @Test
  public void testInterceptorScope_logLevels() {
    Logger logger = logger("foo.bar.Baz");

    LogInterceptor interceptor = Log4jInterceptor.create();
    ImmutableList<LogEntry> logged;
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", INFO)) {

      assertThat(interceptor.getLogs()).isEmpty();

      logger.error("Message: Error");
      logger.warn("Message: Warn");
      logger.info("Message: Info");
      logger.debug("Message: Debug");
      logger.trace("Message: Trace");

      logged = interceptor.getLogs();
      assertThat(logged).hasSize(3);
      assertThat(logged.get(0).getMessage()).isEqualTo("Message: Error");
      assertThat(logged.get(1).getMessage()).isEqualTo("Message: Warn");
      assertThat(logged.get(2).getMessage()).isEqualTo("Message: Info");
    }
  }

  @Test
  public void testWithFlogger() {
    Configurator.setLevel(Log4jInterceptorTest.class.getName(), Level.TRACE);

    FluentLogger logger = FluentLogger.forEnclosingClass();

    LogInterceptor interceptor = Log4jInterceptor.create();
    try (Recorder recorder = interceptor.attachTo(getClass().getName(), INFO)) {
      logger.atWarning().withCause(new IllegalStateException("Oopsie!")).log("Warning: Badness");
      logger.atInfo().with(TAGS, Tags.of("foo", "bar")).log("Hello World");
      logger.atFine().log("Ignore me!");
    }
    ImmutableList<LogEntry> logged = interceptor.getLogs();
    assertThat(logged).hasSize(2);
    LogSubject.assertThat(logged.get(0)).messageContains("Badness");
    LogSubject.assertThat(logged.get(0)).hasCause(IllegalStateException.class);
    LogSubject.assertThat(logged.get(1)).messageContains("Hello");
    LogSubject.assertThat(logged.get(1)).metadataContains("foo", "bar");
  }
}
