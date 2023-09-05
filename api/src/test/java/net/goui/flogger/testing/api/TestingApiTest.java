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
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.TestingApi.TestId;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestingApiTest {
  private static final Instant TIMESTAMP = Instant.now();
  private static final Object THREAD_ID = "<dummy>";

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
          TIMESTAMP,
          THREAD_ID,
          message,
          ImmutableMap.of(),
          null);
    }
  }

  static class FooApi extends TestingApi<FooApi> {
    protected FooApi(Map<String, ? extends Level> levelMap, @Nullable LogInterceptor interceptor) {
      super(levelMap, interceptor);
    }

    @Override
    protected FooApi api() {
      return this;
    }
  }

  @Test
  public void testApi() {
    ImmutableMap<String, Level> levelMap = ImmutableMap.of("foo", INFO, "bar", WARNING);
    TestInterceptor interceptor = new TestInterceptor();
    FooApi myApi = new FooApi(levelMap, interceptor);

    // Loggers are attached only while the API hook is active.
    assertThat(interceptor.attached).isEmpty();
    try (var unused = myApi.install(/* useTestId= */ true)) {
      assertThat(interceptor.attached).isEqualTo(levelMap);

      // Sneaky look behind the scenes to make sure a test ID was added in this context.
      Tags tags = Platform.getContextDataProvider().getTags();
      assertThat(tags.asMap()).containsKey(TestingApi.TEST_ID);
    }
    assertThat(interceptor.attached).isEmpty();

    myApi.assertLogs().matchCount().isEqualTo(4);
    myApi.assertLog(0).hasMessageContaining("attach: foo");
    myApi.assertLog(1).hasMessageContaining("attach: bar");
    myApi.assertLog(2).hasMessageContaining("detach: foo");
    myApi.assertLog(3).hasMessageContaining("detach: bar");
  }

  // Not static since we want to test inner class names.
  private class Inner {}

  @Test
  public void testLoggerName() {
    String classUnderTest = TestingApiTest.class.getName();
    assertThat(TestingApi.loggerNameOf(TestingApiTest.class)).isEqualTo(classUnderTest);
    assertThat(TestingApi.loggerNameOf(Inner.class)).isEqualTo(classUnderTest + ".Inner");

    // Don't expect to be given primitive/array class etc.
    assertThrows(IllegalArgumentException.class, () -> TestingApi.loggerNameOf(int.class));
    assertThrows(IllegalArgumentException.class, () -> TestingApi.loggerNameOf(int[].class));
    assertThrows(
        IllegalArgumentException.class, () -> TestingApi.loggerNameOf(new Object() {}.getClass()));
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
