package net.goui.flogger.testing.core;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Stream;
import net.goui.flogger.testing.core.CapturedLog.LevelCheck;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class LogStreamSubject extends Subject {
  private final ImmutableList<CapturedLog> logs;

  /**
   * Constructor for use by subclasses. If you want to create an instance of this class itself, call
   * {@link Subject#check(String, Object...) check(...)}{@code .that(actual)}.
   */
  private LogStreamSubject(FailureMetadata metadata, @Nullable ImmutableList<CapturedLog> actual) {
    super(metadata, actual);
    this.logs = actual;
  }

  public static Factory<LogStreamSubject, ImmutableList<CapturedLog>> logStreams() {
    return LogStreamSubject::new;
  }

  public static LogStreamSubject assertThat(@Nullable ImmutableList<CapturedLog> actual) {
    return assertAbout(logStreams()).that(actual);
  }

  private LogStreamSubject filter(String name, Level level, Predicate<CapturedLog> check) {
    return check(name + "(" + level + ")")
        .about(logStreams())
        .that(logs.stream().filter(check).collect(toImmutableList()));
  }

  public LogStreamSubject at(Level level) {
    return filter("at", level, log -> log.checkLevel(level) == LevelCheck.COMPATIBLE);
  }

  public LogStreamSubject atOrAbove(Level level) {
    return filter("atOrAbove", level, log -> log.checkLevel(level) != LevelCheck.BELOW);
  }

  public LogStreamSubject above(Level level) {
    return filter("above", level, log -> log.checkLevel(level) == LevelCheck.ABOVE);
  }

  public LogStreamSubject atOrBelow(Level level) {
    return filter("atOrBelow", level, log -> log.checkLevel(level) != LevelCheck.ABOVE);
  }

  public LogStreamSubject below(Level level) {
    return filter("below", level, log -> log.checkLevel(level) == LevelCheck.BELOW);
  }

  private Stream<String> messages() {
    return logs.stream().map(CapturedLog::getMessage);
  }

  public void allMessagesContain(String token) {
    if (!messages().allMatch(m -> m.contains(token))) {
      failWithActual(Fact.fact("all logged messages contain substring", token));
    }
  }

  public void allMessagesMatch(String regex) {
    if (!messages().allMatch(m -> m.matches(regex))) {
      failWithActual(Fact.fact("all logged messages match regular expression", regex));
    }
  }

  public void noMessagesContain(String token) {
    if (messages().anyMatch(m -> m.contains(token))) {
      failWithActual(Fact.fact("no logged message contains substring", token));
    }
  }

  public void noMessagesMatch(String regex) {
    if (messages().anyMatch(m -> m.matches(regex))) {
      failWithActual(Fact.fact("no logged message matches regular expression", regex));
    }
  }

  public Times someMessageContains(String token) {
    long matchCount = messages().map(m -> m.contains(token)).count();
    if (matchCount == 0) {
      failWithActual(Fact.fact("some logged message contains substring", token));
    }
    return n -> {
      checkArgument(n > 0);
      if (matchCount < n) {
        failWithActual(Fact.fact("at least " + n + " logged messages contained substring", token));
      }
    };
  }

  public Times someMessageMatches(String regex) {
    long matchCount = messages().map(m -> m.matches(regex)).count();
    if (matchCount == 0) {
      failWithActual(Fact.fact("some logged message matches regular expression", regex));
    }
    return n -> {
      checkArgument(n > 0);
      if (matchCount < n) {
        failWithActual(Fact.fact("at least " + n + " logged messages match regular expression", regex));
      }
    };
  }

  public interface Times {
    void times(long n);
  }
}
