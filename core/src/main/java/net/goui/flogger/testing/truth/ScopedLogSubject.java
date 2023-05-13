package net.goui.flogger.testing.truth;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.goui.flogger.testing.LevelClass;

public class ScopedLogSubject extends Subject implements LogAssertion {
  private final ScopedLog log;

  protected ScopedLogSubject(FailureMetadata metadata, ScopedLog log) {
    super(metadata, log);
    this.log = log;
  }

  public static Factory<ScopedLogSubject, ScopedLog> scopedLogs() {
    return ScopedLogSubject::new;
  }

  public static ScopedLogSubject assertThat(ScopedLog log) {
    return assertAbout(scopedLogs()).that(log);
  }

  public LogAssertion atLevel(LevelClass level) {
    return filter("aboveLevel", level, c -> c == level);
  }

  public LogAssertion aboveLevel(LevelClass level) {
    return filter("aboveLevel", level, c -> c.compareTo(level) > 0);
  }

  public LogAssertion belowLevel(LevelClass level) {
    return filter("belowLevel", level, c -> c.compareTo(level) < 0);
  }

  public LogAssertion atOrAboveLevel(LevelClass level) {
    return filter("atOrAboveLevel", level, c -> c.compareTo(level) >= 0);
  }

  public LogAssertion atOrBelowLevel(LevelClass level) {
    return filter("atOrBelowLevel", level, c -> c.compareTo(level) <= 0);
  }

  private LogAssertion filter(String name, LevelClass level, Predicate<LevelClass> test) {
    return check("%s(%s)", name, level)
        .about(scopedLogs())
        .that(log.filter(e -> test.test(e.levelClass())));
  }

  @Override
  public void messageContains(String substring) {
    handleResult(
        log.assertLogs(e -> e.getMessage().contains(substring)),
        logFact("message", "contain substring", substring));
  }

  @Override
  public void messageMatches(String regex) {
    Pattern pattern = Pattern.compile(regex);
    handleResult(
        log.assertLogs(e -> pattern.matcher(e.getMessage()).find()),
        logFact("message", "match regular expression", regex));
  }

  @Override
  public void metadataContains(String key, boolean value) {
    metadataContainsImpl(key, value);
  }

  @Override
  public void metadataContains(String key, long value) {
    metadataContainsImpl(key, value);
  }

  @Override
  public void metadataContains(String key, double value) {
    metadataContainsImpl(key, value);
  }

  @Override
  public void metadataContains(String key, String value) {
    metadataContainsImpl(key, value);
  }

  private void metadataContainsImpl(String key, Object value) {
    handleResult(
        log.assertLogs(e -> e.getMetadata().getOrDefault(key, ImmutableList.of()).contains(value)),
        logFact("metadata", "contain", key + "=" + value));
  }

  @Override
  public void hasCause(Class<? extends Throwable> type) {
    handleResult(
        log.assertLogs(e -> type.isInstance(e.getCause())), logFact("cause", "be of type", type));
  }

  @Override
  public void levelIs(LevelClass level) {
    handleResult(
        log.assertLogs(e -> e.levelClass() == level),
        logFact("level", "be compatible with", level));
  }

  @Override
  public void levelIsAbove(LevelClass level) {
    handleResult(
        log.assertLogs(e -> e.levelClass().compareTo(level) > 0),
        logFact("level", "be above", level));
  }

  @Override
  public void levelIsBelow(LevelClass level) {
    handleResult(
        log.assertLogs(e -> e.levelClass().compareTo(level) < 0),
        logFact("level", "be below", level));
  }

  Fact logFact(String attribute, String claim, Object expected) {
    return Fact.fact(
        String.format("expected %s log %s to %s", log.strategyDescription(), attribute, claim),
        expected);
  }

  public void handleResult(LogAssertionResult result, Fact headlineFact) {
    if (!result.passed()) {
      failWithoutActual(headlineFact, result.describeFailure());
    }
  }
}
