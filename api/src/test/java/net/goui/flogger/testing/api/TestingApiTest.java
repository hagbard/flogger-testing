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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.LevelClass.FINE;
import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.LevelClass.SEVERE;
import static net.goui.flogger.testing.LevelClass.WARNING;
import static net.goui.flogger.testing.SetLogLevel.Scope.CLASS_UNDER_TEST;
import static net.goui.flogger.testing.SetLogLevel.Scope.PACKAGE_UNDER_TEST;
import static net.goui.flogger.testing.SetLogLevel.Scope.UNDEFINED;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.SetLogLevel;
import net.goui.flogger.testing.SetLogLevel.Scope;
import net.goui.flogger.testing.api.TestingApi.TestId;
import net.goui.flogger.testing.truth.LogsSubject;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestingApiTest {
  private static final String INTERCEPTOR_CLASS = "other.package.TestInterceptor";
  private static final Instant TIMESTAMP = Instant.now();
  private static final Object THREAD_ID = "<dummy>";

  private static class TestInterceptor implements LogInterceptor {
    private final HashMap<String, Consumer<LogEntry>> logCollectors = new HashMap<>();

    @Override
    public Recorder attachTo(String loggerName, LevelClass level, Consumer<LogEntry> collector) {
      Consumer<LogEntry> filteringCollector =
          RecorderSpec.of(loggerName, level).applyFilter(collector, /* testId */ "");
      checkState(
          logCollectors.put(loggerName, filteringCollector) == null,
          "The same logger name should not be attached multiple times: %s",
          loggerName);
      interceptorLog("attach: " + loggerName);
      return () -> {
        interceptorLog("detach: " + loggerName);
        logCollectors.remove(loggerName);
      };
    }

    private void interceptorLog(String msg) {
      Consumer<LogEntry> collector = findCollector(INTERCEPTOR_CLASS);
      if (collector != null) {
        collector.accept(log(INTERCEPTOR_CLASS, INFO, msg));
      }
    }

    void addLogs(LogEntry... entries) {
      for (LogEntry e : entries) {
        Consumer<LogEntry> collector = findCollector(e.className());
        if (collector != null) {
          collector.accept(e);
        }
      }
    }

    Consumer<LogEntry> findCollector(String name) {
      while (true) {
        Consumer<LogEntry> collector = logCollectors.get(name);
        if (collector != null || name.isEmpty()) {
          return collector;
        }
        int dot = name.lastIndexOf('.');
        name = dot >= 0 ? name.substring(0, dot) : "";
      }
    }
  }

  private static class TestApi extends TestingApi<TestApi> {
    // Most tests only need the simplest test API constructed.
    static TestApi create(String className) {
      TestInterceptor interceptor = new TestInterceptor();
      // Level map must have at least one entry to cause the injector to be attached.
      ImmutableMap<String, LevelClass> levelMap = ImmutableMap.of(className, INFO);
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
    ImmutableMap<String, LevelClass> levelMap =
        ImmutableMap.of(INTERCEPTOR_CLASS, INFO, "foo", INFO, "bar", INFO);
    TestInterceptor interceptor = new TestInterceptor();
    TestApi logs = new TestApi(levelMap, interceptor);

    // Loggers are attached only while the API hook is active.
    assertThat(interceptor.logCollectors).isEmpty();
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      assertThat(interceptor.logCollectors.keySet()).isEqualTo(levelMap.keySet());

      // Sneaky look behind the scenes to make sure a test ID was added in this context.
      Tags tags = Platform.getContextDataProvider().getTags();
      assertThat(tags.asMap()).containsKey("test_id");
    }
    assertThat(interceptor.logCollectors).isEmpty();

    // Includes attach/detach for the interceptor class.
    logs.assertLogs().matchCount().isEqualTo(6);
    LogEntry fooAttach = logs.assertLogs().withMessageContaining("attach: foo").getOnlyMatch();
    LogEntry barAttach = logs.assertLogs().withMessageContaining("attach: bar").getOnlyMatch();
    LogEntry fooDetach =
        logs.assertLogs(after(fooAttach)).withMessageContaining("detach: foo").getOnlyMatch();
    LogEntry barDetach =
        logs.assertLogs(after(barAttach)).withMessageContaining("detach: bar").getOnlyMatch();
    logs.assertLogOrder(fooAttach, barAttach, barDetach, fooDetach);
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
  public void testTestId_idsAreUnique() {
    Set<String> ids = new HashSet<>();
    for (int n = 0; n < 1000; n++) {
      String id = TestId.claim();
      assertThat(id).matches("[0-9A-F]{4}");
      assertThat(ids.add(id)).isTrue();
    }
    ids.forEach(TestId::release);
  }

  @Test
  public void testTestId_excessiveIds() {
    Set<String> ids = new HashSet<>();
    String id;
    do {
      id = TestId.claim();
      ids.add(id);
    } while (!id.isEmpty());
    ids.forEach(TestId::release);
  }

  @Test
  public void testAssertLogs_basicApi() {
    String className = "SomeClass";
    TestApi logs = TestApi.create(className);
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.addTestLogs(
          log(className, INFO, "foo"),
          log(className, INFO, "bar"),
          log(className, WARNING, "foobar"));

      logs.assertLogs().withMessageContaining("foo").matchCount().isEqualTo(2);
      logs.assertLogs().withMessageContaining("bar").matchCount().isEqualTo(2);

      LogEntry foo = logs.assertLogs().withLevel(INFO).withMessageContaining("foo").getOnlyMatch();
      LogEntry bar = logs.assertLogs().withLevel(INFO).withMessageContaining("bar").getOnlyMatch();
      LogEntry foobar = logs.assertLogs().withMessageContaining("foobar").getOnlyMatch();

      logs.assertLogs(before(foobar)).always().haveLevel(INFO);
      logs.assertLogs(before(foobar)).never().haveMessageContaining("foobar");
      logs.assertLogs(after(foobar)).doNotOccur();

      // You can test directly with log index, but this is really fragile and not recommended.
      logs.assertLog(0).message().contains("foo");
      logs.assertLog(0).level().isEqualTo(INFO);
      logs.assertLog(1).message().contains("bar");
      logs.assertLog(1).level().isEqualTo(INFO);

      logs.assertLogOrder(foo, bar, foobar);
      logs.assertLogOrder(foo, foobar);
    }
  }

  @Test
  public void testAssertLogs_withExtraLevelMap() {
    // Merging the main level map and the extra map should result in:
    // "foo.bar" -> INFO, "foo.bar.fine" -> FINE (added) and "foo.baz" -> WARNING (overridden).
    ImmutableMap<String, LevelClass> levelMap = ImmutableMap.of("foo.bar", INFO, "foo.baz", INFO);
    ImmutableMap<String, LevelClass> extraMap =
        ImmutableMap.of("foo.bar.fine", FINE, "foo.baz", WARNING);

    TestApi logs = new TestApi(levelMap, new TestInterceptor());
    try (var unused = logs.install(/* useTestId= */ true, extraMap)) {
      logs.addTestLogs(
          log("foo.bar.Class", INFO, "<expected>"),
          log("foo.bar.Class", FINE, "<unexpected>"),
          log("foo.bar.fine.Class", FINE, "<expected>"),
          log("foo.baz.Class", WARNING, "<expected>"),
          log("foo.baz.Class", INFO, "<unexpected>"));

      logs.assertLogs().withMessageContaining("<expected>").matchCount().isEqualTo(3);
      logs.assertLogs().withMessageContaining("<unexpected>").doNotOccur();
    }
  }

  @Test
  public void testAssertLogs_failureMessages() {
    String className = "SomeClass";
    TestApi logs = TestApi.create(className);
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.addTestLogs(
          log(className, INFO, "foo"),
          log(className, INFO, "bar"),
          log(className, WARNING, "foobar"));

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
    String className = "SomeClass";
    TestApi logs = TestApi.create(className);
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      // No warning logs, so verification passes.
      logs.addTestLogs(log(className, INFO, "foo"), log(className, INFO, "bar"));
    }
  }

  @Test
  public void testVerification_fail() {
    String className = "SomeClass";
    TestApi logs = TestApi.create(className);
    boolean testFailed = false;
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      // Adding a warning log makes verification fail.
      logs.addTestLogs(
          log(className, INFO, "foo"),
          log(className, INFO, "bar"),
          log(className, WARNING, "warning"));
    } catch (AssertionError expected) {
      testFailed = true;
    }
    assertThat(testFailed).isTrue();
  }

  @Test
  public void testVerification_clearVerification() {
    String className = "SomeClass";
    TestApi logs = TestApi.create(className);
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      logs.addTestLogs(
          log(className, INFO, "foo"),
          log(className, INFO, "bar"),
          log(className, WARNING, "warning"));

      // Without this, the warning would cause verification failure when the api is closed, but no
      // other logs are verified either.
      logs.clearVerification();
    }
  }

  @Test
  public void testAssertLogs_expectLogs() {
    String className = "SomeClass";
    TestApi logs = TestApi.create(className);
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      // Expectations can be applied before the logging occurs and are verified after the test.
      // This is a generally discouraged approach to logs testing, but it is supported to help
      // existing code switch to this API.

      // Verify later that the expectations below covered all the logs emitted by this test.
      logs.verify(LogsSubject::doNotOccur);

      // Note that "foobar" is covered by both of the following expectations.
      logs.expectLogs(log -> log.withMessageContaining("foo")).atLeast(1);
      logs.expectLogs(log -> log.withMessageContaining("bar")).atLeast(1);

      // Other variations of the same assertion.
      logs.expectLogs(log -> log.withMessageContaining("foobar")).once();
      logs.expectLogs(log -> log.withMessageContaining("foo")).times(2);
      logs.expectLogs(log -> log.withMessageContaining("foo")).atMost(2);
      logs.expectLogs(log -> log.withMessageContaining("bar")).moreThan(1);
      logs.expectLogs(log -> log.withMessageContaining("bar")).fewerThan(3);

      logs.addTestLogs(
          log(className, INFO, "foo"),
          log(className, INFO, "bar"),
          log(className, WARNING, "foobar"));
    }
  }

  @Test
  public void testVerification_expectedLogsAreNotVerified() {
    String className = "SomeClass";
    TestApi logs = TestApi.create(className);
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      logs.addTestLogs(
          log(className, INFO, "foo"),
          log(className, INFO, "bar"),
          log(className, WARNING, "warning"));

      LogEntry warning =
          logs.assertLogs().withLevel(WARNING).withMessageContaining("warning").getOnlyMatch();

      // Without this, the warning would cause verification failure when the api is closed.
      logs.expect(warning);
    }
  }

  @Test
  public void testVerification_expectedLogsAreUniqueInstances_fail() {
    String className = "SomeClass";
    TestApi logs = TestApi.create(className);
    boolean testFailed = false;
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      // The logs here have identical contents but are not considered equal.
      logs.addTestLogs(
          log(className, INFO, "foo"),
          log(className, WARNING, "warning"),
          log(className, WARNING, "warning"));

      LogEntry firstWarning =
          logs.assertLogs().withLevel(WARNING).withMessageContaining("warning").getMatch(0);

      // Only excluding the first warning means that the second one causes verification to fail.
      logs.expect(firstWarning);
    } catch (AssertionError expected) {
      testFailed = true;
    }
    assertThat(testFailed).isTrue();
  }

  @Test
  public void testVerification_expectedLogsAreUniqueInstances_pass() {
    String className = "SomeClass";
    TestApi logs = TestApi.create(className);
    try (var unused = logs.install(/* useTestId= */ true, ImmutableMap.of())) {
      logs.verify(anyLogs -> anyLogs.withLevelAtLeast(WARNING).doNotOccur());

      logs.addTestLogs(
          log(className, INFO, "foo"),
          log(className, WARNING, "warning"),
          log(className, WARNING, "warning"));

      LogsSubject warnings = logs.assertLogs().withLevel(WARNING).withMessageContaining("warning");

      // Excluding all warnings makes the verification pass.
      logs.expect(warnings.getAllMatches());
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
    assertThrows(
        IllegalArgumentException.class,
        () -> getLevelMap(testClass, setLogLevel(testClass, "foo.Class", UNDEFINED, INFO)));
    assertThrows(
        IllegalArgumentException.class,
        () -> getLevelMap(testClass, setLogLevel(testClass, "", CLASS_UNDER_TEST, INFO)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            getLevelMap(
                testClass, setLogLevel(Object.class, "foo.Class", PACKAGE_UNDER_TEST, WARNING)));
    assertThrows(
        IllegalArgumentException.class,
        () -> getLevelMap(testClass, setLogLevel(Object.class, "", UNDEFINED, INFO)));
  }

  private static ImmutableList<SetLogLevel> setLogLevel(
      Class<?> target, String cname, Scope scope, LevelClass level) {
    return ImmutableList.of(TestSetLogLevel.of(target, cname, scope, level));
  }

  private static void assertFailureContains(ThrowingRunnable fn, String... fragments) {
    AssertionError failure = assertThrows(AssertionError.class, fn);
    for (String s : fragments) {
      assertThat(failure).hasMessageThat().contains(s);
    }
  }

  private static LogEntry log(String className, LevelClass level, String message) {
    return LogEntry.of(
        className,
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
