/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.api;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.LevelClass.FINE;
import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.LevelClass.SEVERE;
import static net.goui.flogger.testing.LevelClass.WARNING;
import static net.goui.flogger.testing.SetLogLevel.Scope.CLASS_UNDER_TEST;
import static net.goui.flogger.testing.SetLogLevel.Scope.PACKAGE_UNDER_TEST;
import static net.goui.flogger.testing.api.TestingApi.getLevelMap;
import static net.goui.flogger.testing.api.TestingApi.guessClassUnderTest;
import static net.goui.flogger.testing.api.TestingApi.guessPackageUnderTest;
import static net.goui.flogger.testing.truth.LogMatcher.after;
import static net.goui.flogger.testing.truth.LogMatcher.before;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.context.Tags;
import com.google.common.truth.Truth;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.SetLogLevel;
import net.goui.flogger.testing.api.TestingApi.TestId;
import net.goui.flogger.testing.truth.LogsSubject;
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

  private static class TestApi extends TestingApi<TestApi> {
    // Most tests only need the simplest test API constructed.
    static TestApi create() {
      TestInterceptor interceptor = new TestInterceptor();
      // Level map must have at least one entry to cause the injector to be attached.
      ImmutableMap<String, LevelClass> levelMap = ImmutableMap.of("<anything>", INFO);
      return new TestApi(levelMap, interceptor);
    }

    private final TestInterceptor interceptor;

    TestApi(Map<String, LevelClass> levelMap, TestInterceptor interceptor) {
      super(levelMap, interceptor);
      this.interceptor = interceptor;
    }

    @Override
    protected TestApi api() {
      return this;
    }

    void addTestLogs(LogEntry... entries) {
      interceptor.addLogs(entries);
    }
  }

  @Test
  public void testApiInstall() {
    ImmutableMap<String, LevelClass> levelMap = ImmutableMap.of("foo", INFO, "bar", INFO);
    TestInterceptor interceptor = new TestInterceptor();
    TestApi logs = new TestApi(levelMap, interceptor);

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
    TestApi logs = TestApi.create();
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.addTestLogs(log(INFO, "foo"), log(INFO, "bar"), log(WARNING, "foobar"));

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
    TestApi logs = TestApi.create();
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.addTestLogs(log(INFO, "foo"), log(INFO, "bar"), log(WARNING, "foobar"));

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

  @Test
  public void testVerification_pass() {
    TestApi logs = TestApi.create();
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      // No warning logs, so verification passes.
      logs.addTestLogs(log(INFO, "foo"), log(INFO, "bar"));
    }
  }

  @Test
  public void testVerification_fail() {
    TestApi logs = TestApi.create();
    boolean testFailed = false;
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      // Adding a warning log makes verification fail.
      logs.addTestLogs(log(INFO, "foo"), log(INFO, "bar"), log(WARNING, "warning"));
    } catch (AssertionError expected) {
      testFailed = true;
    }
    assertThat(testFailed).isTrue();
  }

  @Test
  public void testVerification_clearVerification() {
    TestApi logs = TestApi.create();
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      logs.addTestLogs(log(INFO, "foo"), log(INFO, "bar"), log(WARNING, "warning"));

      // Without this, the warning would cause verification failure when the api is closed, but no
      // other logs are verified either.
      logs.clearVerification();
    }
  }

  @Test
  public void testVerification_exclude() {
    TestApi logs = TestApi.create();
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      logs.addTestLogs(log(INFO, "foo"), log(INFO, "bar"), log(WARNING, "warning"));

      LogEntry warning =
          logs.assertLogs().withLevel(WARNING).withMessageContaining("warning").getOnlyMatch();

      // Without this, the warning would cause verification failure when the api is closed.
      logs.excludeFromVerification(warning);
    }
  }

  @Test
  public void testVerification_excludedLogsAreUniqueInstances_fail() {
    TestApi logs = TestApi.create();
    boolean testFailed = false;
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      // The logs here have identical contents but are not considered equal.
      logs.addTestLogs(log(INFO, "foo"), log(WARNING, "warning"), log(WARNING, "warning"));

      LogEntry firstWarning =
          logs.assertLogs().withLevel(WARNING).withMessageContaining("warning").getMatch(0);

      // Only excluding the first warning means that the second one causes verification to fail.
      logs.excludeFromVerification(firstWarning);
    } catch (AssertionError expected) {
      testFailed = true;
    }
    assertThat(testFailed).isTrue();
  }

  @Test
  public void testVerification_excludedLogsAreUniqueInstances_pass() {
    TestApi logs = TestApi.create();
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      logs.addTestLogs(log(INFO, "foo"), log(WARNING, "warning"), log(WARNING, "warning"));

      LogsSubject warnings = logs.assertLogs().withLevel(WARNING).withMessageContaining("warning");

      // Excluding all warnings makes the verification pass.
      logs.excludeFromVerification(warnings.getAllMatches());
    }
  }

  @Test
  public void testAssertLogs_expectations() {
    TestApi logs = TestApi.create();
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      // Assert there are no unaccounted for logs once all expectations are accounted for.
      logs.verify(LogsSubject::doNotOccur);

      // Expectations can be applied before the logging occurs and are verified after the test.
      // This is a generally discouraged approach to logs testing, but it is supported to help
      // existing code switch to this API.
      //
      // NOTE: This is exactly why using "expectations" over log entries is brittle. It's basically
      // the same as having a mocked logger, and encourages the sort of testing which has to isolate
      // exactly where log statements are. In this case the test API does its own bit of logging
      // before/after everything else, which we must also make an expectation for. This leaks the
      // existence of these other log statements (which no other test cares about) into this test.
      logs.expectLogs(log -> log.withMessageContaining("anything")).atLeast(1);
      // Note that "foobar" is covered by both of the following expectations.
      logs.expectLogs(log -> log.withMessageContaining("foo")).atLeast(1);
      logs.expectLogs(log -> log.withMessageContaining("bar")).atLeast(1);

      logs.addTestLogs(log(INFO, "foo"), log(INFO, "bar"), log(WARNING, "foobar"));
    }
  }

  @Test
  public void testGuessClassUnderTest() {
    // a.b.XxxTest.class --> "a.b.Xxx"
    assertThat(guessClassUnderTest(TestingApiTest.class)).isEqualTo(TestingApi.class.getName());
    assertThrows(IllegalArgumentException.class, () -> guessClassUnderTest(String.class));
  }

  @Test
  public void testGuessPackageUnderTest() {
    // a.b.XxxTest.class --> "a.b"
    Truth.assertThat(guessPackageUnderTest(TestingApiTest.class))
        .isEqualTo(getClass().getPackage().getName());
  }

  @Test
  public void testGetLevelMap() {
    Class<TestingApiTest> testClass = TestingApiTest.class;
    ImmutableMap<String, LevelClass> levelMap =
        getLevelMap(
            testClass,
            ImmutableList.of(
                TestSetLogLevel.of(testClass, SEVERE),
                TestSetLogLevel.of("foo.bar", WARNING),
                TestSetLogLevel.of(PACKAGE_UNDER_TEST, INFO),
                TestSetLogLevel.of(CLASS_UNDER_TEST, FINE)));
    assertThat(levelMap).containsEntry(testClass.getName(), SEVERE);
    assertThat(levelMap).containsEntry("foo.bar", WARNING);
    assertThat(levelMap).containsEntry(testClass.getPackage().getName(), INFO);
    assertThat(levelMap).containsEntry(TestingApi.class.getName(), FINE);
  }

  @Test
  public void testGetLevelMap_duplicateTarget() {
    Class<TestingApiTest> testClass = TestingApiTest.class;
    assertThrows(
        IllegalArgumentException.class,
        () ->
            getLevelMap(
                testClass,
                ImmutableList.of(
                    TestSetLogLevel.of(TestingApi.class, SEVERE),
                    TestSetLogLevel.of(CLASS_UNDER_TEST, FINE))));
  }

  @Test
  public void testGetLevelMap_badName() {
    Class<TestingApiTest> testClass = TestingApiTest.class;
    SetLogLevel badAnnotation = TestSetLogLevel.of("ClassOnly", INFO);
    assertThrows(
        IllegalArgumentException.class,
        () -> getLevelMap(testClass, ImmutableList.of(badAnnotation)));
  }

  @Test
  public void testGetLevelMap_badAnnotation() {
    Class<TestingApiTest> testClass = TestingApiTest.class;
    SetLogLevel badAnnotation = TestSetLogLevel.of(testClass, "foo.Class", null, INFO);
    assertThrows(
        IllegalArgumentException.class,
        () -> getLevelMap(testClass, ImmutableList.of(badAnnotation)));
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
