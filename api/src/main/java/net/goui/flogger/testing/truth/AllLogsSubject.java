package net.goui.flogger.testing.truth;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;

/** Assertions to be applied to all matched log entries. */
public class AllLogsSubject extends Subject {
  static Factory<AllLogsSubject, ImmutableList<LogEntry>> logs(
      String label, BiPredicate<Stream<LogEntry>, Predicate<? super LogEntry>> op) {
    return (subject, logs) -> new AllLogsSubject(subject, logs, label, op);
  }

  private final ImmutableList<LogEntry> logs;
  private final String label;
  private final BiPredicate<Stream<LogEntry>, Predicate<? super LogEntry>> op;

  private AllLogsSubject(
      FailureMetadata metadata,
      ImmutableList<LogEntry> logs,
      String label,
      BiPredicate<Stream<LogEntry>, Predicate<? super LogEntry>> op) {
    super(metadata, logs);
    this.logs = logs;
    this.label = label;
    this.op = op;
  }

  /** Asserts that matched log entries have messages containing the specified substring. */
  public void haveMessageContaining(String fragment) {
    if (!op.test(logs.stream(), e -> e.message().contains(fragment))) {
      failWithActual(label + " matched logs were expected to contain", fragment);
    }
  }

  /**
   * Asserts that matched log entries have messages containing a match to the specified regular
   * expression.
   */
  public void haveMessageMatching(String regex) {
    Predicate<String> regexPredicate = Pattern.compile(regex).asPredicate();
    if (!op.test(logs.stream(), e -> regexPredicate.test(e.message()))) {
      failWithActual(label + " matched logs were expected to match", regex);
    }
  }

  /** Asserts that matched log entries are at the given level. */
  public void areAtLevel(LevelClass level) {
    if (!op.test(logs.stream(), e -> e.levelClass() == level)) {
      failWithActual(label + " matched logs were expected be at level", level);
    }
  }

  /** Asserts that matched log entries are above the given level. */
  public void areAboveLevel(LevelClass level) {
    if (!op.test(logs.stream(), e -> e.levelClass().compareTo(level) > 0)) {
      failWithActual(label + " matched logs were expected be at level", level);
    }
  }

  /** Asserts that matched log entries are at or above the given level. */
  public void areAtOrAboveLevel(LevelClass level) {
    if (!op.test(logs.stream(), e -> e.levelClass().compareTo(level) >= 0)) {
      failWithActual(label + " matched logs were expected be at level", level);
    }
  }

  /** Asserts that matched log entries are below the given level. */
  public void areBelowLevel(LevelClass level) {
    if (!op.test(logs.stream(), e -> e.levelClass().compareTo(level) < 0)) {
      failWithActual(label + " matched logs were expected be at level", level);
    }
  }

  /** Asserts that matched log entries are at or below the given level. */
  public void areAtOrBelowLevel(LevelClass level) {
    if (!op.test(logs.stream(), e -> e.levelClass().compareTo(level) <= 0)) {
      failWithActual(label + " matched logs were expected be at level", level);
    }
  }

  /** Asserts that matched log entries have a cause of the specified type. */
  public void haveCause(Class<? extends Throwable> clazz) {
    if (!op.test(logs.stream(), e -> clazz.isInstance(e.cause()))) {
      failWithActual(label + " matched logs were expected have cause of type", clazz.getName());
    }
  }

  /** Asserts that matched log entries have the specified metadata key-value pair. */
  public void haveMetadata(String key, @Nullable Object value) {
    haveMetadataImpl(key, value);
  }

  /** Asserts that matched log entries have the specified metadata key-value pair. */
  public void haveMetadata(String key, long value) {
    haveMetadataImpl(key, value);
  }

  /** Asserts that matched log entries have the specified metadata key-value pair. */
  public void haveMetadata(String key, double value) {
    haveMetadataImpl(key, value);
  }

  /** Asserts that matched log entries have the specified metadata key-value pair. */
  public void haveMetadata(String key, boolean value) {
    haveMetadataImpl(key, value);
  }

  private void haveMetadataImpl(String key, @Nullable Object value) {
    if (!op.test(logs.stream(), e -> e.hasMetadata(key, value))) {
      failWithActual(label + " matched logs were expected have metadata", key + "=" + value);
    }
  }

  /** Asserts that matched log entries have the specified metadata key. */
  public void haveMetadataKey(String key) {
    if (!op.test(logs.stream(), e -> e.hasMetadata(key, null))) {
      failWithActual(label + " matched logs were expected have metadata key", key);
    }
  }
}
