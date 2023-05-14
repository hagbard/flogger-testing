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
import net.goui.flogger.testing.truth.LogsSubject;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Log4jInterceptorTest {
  Logger logger(String name) {
    Logger logger = (Logger) LogManager.getLogger(name);
    logger.setLevel(Level.TRACE);
    return logger;
  }

  @Test
  public void testInterceptorScope() {
    LogInterceptor interceptor = Log4jInterceptor.create();
    ImmutableList<LogEntry> logged;
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", INFO)) {
      Logger logger = logger("foo.bar.Baz");
      Logger childLogger = logger("foo.bar.Baz.Child");
      Logger parentLogger = logger("foo.bar");
      Logger siblingLogger = logger("foo.bar.Sibling");

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
    assertThat(interceptor.getLogs()).isEqualTo(logged);
  }

  @Test
  public void testWithFlogger() {
    FluentLogger logger = FluentLogger.forEnclosingClass();
    LogInterceptor interceptor = Log4jInterceptor.create();
    try (Recorder recorder = interceptor.attachTo(getClass().getName(), INFO)) {
      ((Logger) LogManager.getLogger(getClass().getName())).setLevel(Level.TRACE);
      logger.atInfo().with(TAGS, Tags.of("foo", "bar")).log("Hello World");
    }
    ImmutableList<LogEntry> logged = interceptor.getLogs();
    assertThat(logged).hasSize(1);
    LogsSubject.assertThat(logged).get(0).messageContains("Hello");
    LogsSubject.assertThat(logged).get(0).metadataContains("foo", "bar");
  }
}
