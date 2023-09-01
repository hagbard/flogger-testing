package net.goui.flogger.testing.api;

import static com.google.common.truth.Truth.assertThat;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.context.Tags;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.TestApi.TestId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestApiTest {
  private static final String PACKAGE_NAME = TestApi.class.getPackage().getName();

  private static class TestInterceptor implements LogInterceptor {
    final HashMap<String, Level> attached = new HashMap<>();

    @Override
    public Recorder attachTo(
        String loggerName, Level level, Consumer<LogEntry> collector, String testId) {
      attached.put(loggerName, level);
      collector.accept(logEntry("attach: " + loggerName));
      return () -> {
        collector.accept(logEntry("detach: " + loggerName));
        attached.remove(loggerName);
      };
    }

    private static LogEntry logEntry(String message) {
      return LogEntry.of(
          "class",
          "method",
          "info",
          LevelClass.INFO,
          Instant.now(),
          message,
          ImmutableMap.of(),
          null);
    }
  }

  @Test
  public void testApi() {
    ImmutableMap<String, Level> levelMap = ImmutableMap.of("foo", INFO, "bar", WARNING);
    TestInterceptor interceptor = new TestInterceptor();
    TestApi myApi = new TestApi(levelMap, interceptor, null) {};

    // Loggers are attached only while the API hook is active.
    assertThat(interceptor.attached).isEmpty();
    try (TestApi.ApiHook install = myApi.install(/* useTestId= */ true)) {
      assertThat(interceptor.attached).isEqualTo(levelMap);

      // Sneaky look behind the scenes to make sure a test ID was added in this context.
      Tags tags = Platform.getContextDataProvider().getTags();
      assertThat(tags.asMap()).containsKey(TestApi.TEST_ID);
    }
    assertThat(interceptor.attached).isEmpty();

    myApi.assertThat().logCount().isEqualTo(4);
    myApi.assertLog(0).contains("attach: foo");
    myApi.assertLog(1).contains("attach: bar");
    myApi.assertLog(2).contains("detach: foo");
    myApi.assertLog(3).contains("detach: bar");
  }

  // Not static since we want to test inner class names.
  private class Inner {}

  @Test
  public void testLoggerName() {
    assertThat(TestApi.loggerNameOf(TestApiTest.class)).isEqualTo(PACKAGE_NAME + ".TestApiTest");
    assertThat(TestApi.loggerNameOf(Inner.class)).isEqualTo(PACKAGE_NAME + ".TestApiTest.Inner");

    // Don't expect to be given primitive/array class etc.
    assertThrows(IllegalArgumentException.class, () -> TestApi.loggerNameOf(int.class));
    assertThrows(IllegalArgumentException.class, () -> TestApi.loggerNameOf(int[].class));
    assertThrows(
        IllegalArgumentException.class, () -> TestApi.loggerNameOf(new Object() {}.getClass()));
  }

  @Test
  public void testTestUniqueIds() {
    Set<String> ids = new HashSet<>();
    for (int n = 0; n < 1000; n++) {
      String id = TestId.claim();
      assertThat(id).matches("[0-9A-F]{4}");
      assertThat(ids.add(id)).isTrue();
    }
    ids.forEach(TestId::release);
  }
}
