package net.goui.flogger.testing.jdk;

import static com.google.common.truth.Truth.assertThat;
import static java.util.logging.Level.INFO;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.logging.Logger;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.LogInterceptor.Recorder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JdkInterceptorTest {
  Logger logger(String name) {
    Logger logger = Logger.getLogger(name);
    logger.setLevel(INFO);
    return logger;
  }

  @Test
  public void testInterceptorScope() {
    Logger jdkLogger = logger("foo.bar.Baz");
    Logger childLogger = logger("foo.bar.Baz.Child");
    Logger parentLogger = logger("foo.bar");
    Logger siblingLogger = logger("foo.bar.Sibling");

    LogInterceptor interceptor = JdkInterceptor.create();
    ImmutableList<LogEntry> logged;
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", INFO)) {
      assertThat(interceptor.getLogs()).isEmpty();

      jdkLogger.info("Log message");
      childLogger.info("Child message");
      parentLogger.info("Parent message");
      siblingLogger.info("Sibling message");

      logged = interceptor.getLogs();
      assertThat(logged).hasSize(2);
      assertThat(logged.get(0).message()).isEqualTo("Log message");
      assertThat(logged.get(1).message()).isEqualTo("Child message");
    }
    jdkLogger.info("After test!");
    assertThat(interceptor.getLogs()).isEqualTo(logged);
  }

  @Test
  public void testMetadata_allTypes() {
    Logger jdkLogger = logger("foo.bar.Baz");
    LogInterceptor interceptor = JdkInterceptor.create();
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", INFO)) {
      jdkLogger.warning("Message [CONTEXT foo=true ]");
      jdkLogger.warning("Message [CONTEXT bar=1234 ]");
      jdkLogger.warning("Message [CONTEXT bar=1.23e6 ]");
      jdkLogger.warning("Message [CONTEXT baz=\"\\tline1\\n\\t\\\"line2\\\"\" ]");
      jdkLogger.warning("Message [CONTEXT key ]");

      ImmutableList<LogEntry> logged = interceptor.getLogs();
      assertThat(logged.get(0).metadata()).containsExactly("foo", List.of(true));
      assertThat(logged.get(1).metadata()).containsExactly("bar", List.of(1234L));
      assertThat(logged.get(2).metadata()).containsExactly("bar", List.of(1.23e6D));
      assertThat(logged.get(3).metadata())
          .containsExactly("baz", List.of("\tline1\n\t\"line2\""));
      assertThat(logged.get(4).metadata()).containsExactly("key", List.of());
    }
  }

  @Test
  public void testMetadata_multipleValues() {
    Logger jdkLogger = logger("foo.bar.Baz");
    LogInterceptor interceptor = JdkInterceptor.create();
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", INFO)) {
      jdkLogger.warning(
          "Message [CONTEXT foo=true foo=1234 foo=1.23e6 foo=\"\\tline1\\n\\t\\\"line2\\\"\" ]");

      ImmutableList<LogEntry> logged = interceptor.getLogs();
      assertThat(logged.get(0).metadata())
          .containsExactly("foo", List.of(true, 1234L, 1.23e6D, "\tline1\n\t\"line2\""));
    }
  }
}
