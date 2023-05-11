package net.goui.flogger.testing.core.truth;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.goui.flogger.testing.core.LogEntry;

@AutoValue
public abstract class ScopedLog {
  private static final Predicate<LogEntry> ALL_LOGS = log -> true;

  interface TestStrategy
      extends BiFunction<Supplier<Stream<LogEntry>>, Predicate<LogEntry>, LogAssertionResult> {}

  abstract ImmutableList<LogEntry> log();

  abstract Predicate<LogEntry> testFilter();

  abstract String strategyDescription();

  abstract TestStrategy strategy();

  public final ScopedLog filter(Predicate<LogEntry> predicate) {
    return new AutoValue_ScopedLog(
        log(), testFilter().and(predicate), strategyDescription(), strategy());
  }

  public static ScopedLog everyMatch(ImmutableList<LogEntry> log) {
    return new AutoValue_ScopedLog(log, ALL_LOGS, "every", ScopedLog::testEveryMatch);
  }

  public static ScopedLog noMatch(ImmutableList<LogEntry> log) {
    return new AutoValue_ScopedLog(log, ALL_LOGS, "no", ScopedLog::testNoMatch);
  }

  public static ScopedLog anyMatch(ImmutableList<LogEntry> log) {
    return new AutoValue_ScopedLog(log, ALL_LOGS, "any", ScopedLog::testAnyMatch);
  }

  public final LogAssertionResult assertLogs(Predicate<LogEntry> logAssertion) {
    return strategy().apply(() -> log().stream().filter(testFilter()), logAssertion);
  }

  private static LogAssertionResult testMatch(
      Supplier<Stream<LogEntry>> logsUnderTest, Predicate<LogEntry> failures) {
    ImmutableList<LogEntry> failedLogs =
        logsUnderTest.get().filter(failures).limit(10).collect(toImmutableList());
    if (failedLogs.isEmpty()) {
      return LogAssertionResult.PASS;
    }
    String prefix = failedLogs.size() < 10 ? "failing logs" : "first few failing logs";
    return LogAssertionResult.fail(
        Fact.simpleFact("but it was not true"), Fact.fact(prefix, failedLogs));
  }

  private static LogAssertionResult testEveryMatch(
      Supplier<Stream<LogEntry>> logsUnderTest, Predicate<LogEntry> logAssertion) {
    return testMatch(logsUnderTest, Predicate.not(logAssertion));
  }

  private static LogAssertionResult testNoMatch(
      Supplier<Stream<LogEntry>> logsUnderTest, Predicate<LogEntry> logAssertion) {
    return testMatch(logsUnderTest, logAssertion);
  }

  private static LogAssertionResult testAnyMatch(
      Supplier<Stream<LogEntry>> logsUnderTest, Predicate<LogEntry> logAssertion) {
    if (logsUnderTest.get().anyMatch(logAssertion)) {
      return LogAssertionResult.PASS;
    }
    ImmutableList<LogEntry> failedLogs = logsUnderTest.get().limit(10).collect(toImmutableList());
    String prefix = failedLogs.size() < 10 ? "failing logs" : "first few failing logs";
    return LogAssertionResult.fail(
        Fact.simpleFact("but it was not true"), Fact.fact(prefix, failedLogs));
  }
}
