/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

This program and the accompanying materials are made available under the terms of the
Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.api;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.LevelClass.WARNING;
import static net.goui.flogger.testing.truth.LogMatcher.after;
import static net.goui.flogger.testing.truth.LogMatcher.before;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.context.Tags;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.TestingApi.TestId;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestingApiTest {
  private static final Instant TIMESTAMP = Instant.now();
  private static final Object THREAD_ID = "<dummy>";

  private static class TestInterceptor implements LogInterceptor {
    private final HashMap<String, LevelClass> attached = new HashMap<>();
    private Consumer<LogEntry> logCollector = null;

    @Override
    public Recorder attachTo(
        String loggerName, LevelClass level, Consumer<LogEntry> collector, String testId) {
      if (logCollector == null) {
        logCollector = checkNotNull(collector);
      }
      attached.put(loggerName, level);
      logCollector.accept(log(INFO, "attach: " + loggerName));
      return () -> {
        logCollector.accept(log(INFO, "detach: " + loggerName));
        attached.remove(loggerName);
      };
    }

    void addLogs(LogEntry... entries) {
      Arrays.stream(entries).forEach(logCollector);
    }
  }

  private static class FooApi extends TestingApi<FooApi> {
    protected FooApi(Map<String, LevelClass> levelMap, @Nullable LogInterceptor interceptor) {
      super(levelMap, interceptor);
    }

    @Override
    protected FooApi api() {
      return this;
    }
  }

  @Test
  public void testApiInstall() {
    ImmutableMap<String, LevelClass> levelMap = ImmutableMap.of("foo", INFO, "bar", INFO);
    TestInterceptor interceptor = new TestInterceptor();
    FooApi logs = new FooApi(levelMap, interceptor);

    // Loggers are attached only while the API hook is active.
    assertThat(interceptor.attached).isEmpty();
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      assertThat(interceptor.attached).isEqualTo(levelMap);

      // Sneaky look behind the scenes to make sure a test ID was added in this context.
      Tags tags = Platform.getContextDataProvider().getTags();
      assertThat(tags.asMap()).containsKey(TestingApi.TEST_ID);
    }
    assertThat(interceptor.attached).isEmpty();

    logs.assertLogs().matchCount().isEqualTo(4);
    LogEntry fooAttach = logs.assertLogs().withMessageContaining("attach: foo").getOnlyMatch();
    LogEntry barAttach = logs.assertLogs().withMessageContaining("attach: bar").getOnlyMatch();
    logs.assertLogs(after(fooAttach))
        .withMessageContaining("detach: foo")
        .matchCount()
        .isEqualTo(1);
    logs.assertLogs(after(barAttach))
        .withMessageContaining("detach: bar")
        .matchCount()
        .isEqualTo(1);
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

  @Test
  public void testAssertLogs_basicApi() {
    TestInterceptor interceptor = new TestInterceptor();
    // Level map must have at least one entry to cause the injector to be attached.
    ImmutableMap<String, LevelClass> levelMap = ImmutableMap.of("<anything>", INFO);
    FooApi logs = new FooApi(levelMap, interceptor);
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      // Remember that (for now) the test API also "logs" something when it's attached.
      interceptor.addLogs(log(INFO, "foo"), log(INFO, "bar"), log(WARNING, "foobar"));

      logs.assertLogs().withMessageContaining("foo").matchCount().isEqualTo(2);
      logs.assertLogs().withMessageContaining("bar").matchCount().isEqualTo(2);
      LogEntry foobar = logs.assertLogs().withMessageContaining("foobar").getOnlyMatch();

      logs.assertLogs(before(foobar)).always().haveLevel(INFO);
      logs.assertLogs(before(foobar)).never().haveMessageContaining("quux");
      logs.assertLogs(after(foobar)).doNotOccur();
    }
  }

  @Test
  public void testAssertLogs_failureMessages() {
    TestInterceptor interceptor = new TestInterceptor();
    // Level map must have at least one entry to cause the injector to be attached.
    ImmutableMap<String, LevelClass> levelMap = ImmutableMap.of("<anything>", INFO);
    FooApi logs = new FooApi(levelMap, interceptor);
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      // Remember that (for now) the test API also "logs" something when it's attached.
      interceptor.addLogs(log(INFO, "foo"), log(INFO, "bar"), log(WARNING, "foobar"));

      assertFailureContains(
          () -> logs.assertLogs().withMessageContaining("foo").doNotOccur(),
          "logs.withMessageContaining('foo')",
          "was expected to be empty");

      assertFailureContains(
          () -> logs.assertLogs().withLevel(INFO).always().haveMessageContaining("foo"),
          "logs.withLevel(INFO).always()",
          "all matched logs were expected to contain");

      assertFailureContains(
          () -> logs.assertLogs().withLevel(INFO).never().haveMessageContaining("foo"),
          "logs.withLevel(INFO).never()",
          "no matched logs were expected to contain");
    }
  }

  private static void assertFailureContains(ThrowingRunnable fn, String... fragments) {
    AssertionError failure = assertThrows(AssertionError.class, fn);
    for (String s : fragments) {
      assertThat(failure).hasMessageThat().contains(s);
    }
  }

  private static LogEntry log(LevelClass level, String message) {
    return LogEntry.of(
        "class",
        "method",
        level.name(),
        level,
        TIMESTAMP,
        THREAD_ID,
        message,
        ImmutableMap.of(),
        null);
  }
}
