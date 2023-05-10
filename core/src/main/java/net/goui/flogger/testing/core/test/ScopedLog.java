package net.goui.flogger.testing.core.test;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

@AutoValue
public abstract class ScopedLog {
  interface TestStrategy
      extends BiFunction<Stream<LogEntry>, Predicate<LogEntry>, LogAssertionResult> {}

  abstract ImmutableList<LogEntry> log();

  abstract String strategyDescription();

  abstract TestStrategy strategy();

  public static ScopedLog every(ImmutableList<LogEntry> log) {
    return new AutoValue_ScopedLog(log, "every", ScopedLog::testEvery);
  }

  public final LogAssertionResult assertLogs(Predicate<LogEntry> logAssertion) {
    return strategy().apply(log().stream(), logAssertion);
  }

  private static LogAssertionResult testEvery(
      Stream<LogEntry> log, Predicate<LogEntry> logAssertion) {
    ImmutableList<LogEntry> firstFewUnexpectedLogs =
        log.filter(Predicate.not(logAssertion)).limit(10).collect(toImmutableList());
    if (firstFewUnexpectedLogs.isEmpty()) {
      return LogAssertionResult.PASS;
    }
    return LogAssertionResult.fail(
        Fact.simpleFact("but they did not"),
        Fact.fact("first few unexpected logs", firstFewUnexpectedLogs));
  }
}
