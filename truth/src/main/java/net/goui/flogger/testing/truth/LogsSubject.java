package net.goui.flogger.testing.truth;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;

@CheckReturnValue
public class LogsSubject extends Subject {
  private final ImmutableList<LogEntry> logs;

  protected LogsSubject(FailureMetadata metadata, ImmutableList<LogEntry> logs) {
    super(metadata, logs);
    this.logs = logs;
  }

  public static Factory<LogsSubject, ImmutableList<LogEntry>> logs() {
    return LogsSubject::new;
  }

  public static LogsSubject assertThat(ImmutableList<LogEntry> log) {
    return assertAbout(logs()).that(log);
  }

  private static ImmutableList<LogEntry> filter(
      ImmutableList<LogEntry> logs, Predicate<LogEntry> predicate) {
    return logs.stream().filter(predicate).collect(toImmutableList());
  }

  /**
   * Matches the subsequence of captured logs which came after the specified entry.
   *
   * <p>Note that when logs are captured in different threads, the order in which they appear may
   * not be the same at the order or their timestamps. This method does not attempt to examine
   * timestamps, and adheres only to the order in which logs are captured.
   *
   * @throws IllegalArgumentException if the given log entry is not in the currently matched
   *     sequence of entries.
   */
  public LogsSubject afterLog(LogEntry entry) {
    return check("afterLog('%s')", entry.snippet())
        .about(LogsSubject.logs())
        .that(filterAfter(entry));
  }

  /**
   * Matches the subsequence of captured logs which came before the specified entry.
   *
   * <p>Note that when logs are captured in different threads, the order in which they appear may
   * not be the same at the order or their timestamps. This method does not attempt to examine
   * timestamps, and adheres only to the order in which logs are captured.
   *
   * @throws IllegalArgumentException if the given log entry is not in the currently matched
   *     sequence of entries.
   */
  public LogsSubject beforeLog(LogEntry entry) {
    return check("beforeLog('%s')", entry.snippet())
        .about(LogsSubject.logs())
        .that(filterBefore(entry));
  }

  /** Matches the subsequence of captured logs with messages containing the specified substring. */
  public LogsSubject withMessageContaining(String fragment) {
    checkNotNull(fragment);
    return check("withMessageContaining('%s')", fragment)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.message().contains(fragment)));
  }

  /**
   * Matches the subsequence of captured logs with messages containing a match to the specified
   * regular expression.
   */
  public LogsSubject withMessageMatching(String regex) {
    Predicate<String> regexPredicate = Pattern.compile(regex).asPredicate();
    return check("withMessageMatching('%s')", regex)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> regexPredicate.test(e.message())));
  }

  /** Matches the subsequence of captured logs at the specified level. */
  public LogsSubject atLevel(LevelClass level) {
    return check("atLevel('%s')", level)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.levelClass() == level));
  }

  /** Matches the subsequence of captured logs strictly above the specified level. */
  public LogsSubject aboveLevel(LevelClass level) {
    return check("aboveLevel('%s')", level)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) > 0));
  }

  /** Matches the subsequence of captured logs at or above the specified level. */
  public LogsSubject atOrAboveLevel(LevelClass level) {
    return check("atOrAboveLevel('%s')", level)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) >= 0));
  }

  /** Matches the subsequence of captured logs strictly below the specified level. */
  public LogsSubject belowLevel(LevelClass level) {
    return check("belowLevel('%s')", level)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) < 0));
  }

  /** Matches the subsequence of captured logs at or below the specified level. */
  public LogsSubject atOrBelowLevel(LevelClass level) {
    return check("atOrBelowLevel('%s')", level)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) <= 0));
  }

  /** Matches the subsequence of captured logs with a cause of the specified type. */
  public LogsSubject withCause(Class<? extends Throwable> clazz) {
    return check("withCause(%s)", clazz.getName())
        .about(LogsSubject.logs())
        .that(filter(logs, e -> hasCause(e, clazz)));
  }

  /** Matches the subsequence of captured logs with the specified metadata key-value pair. */
  public LogsSubject withMetadata(String key, Object value) {
    return check("withMetadata('%s', %s)", key, quoteIfString(value))
        .about(LogsSubject.logs())
        .that(filter(logs, e -> hasMetadata(e, key, value)));
  }

  /** Matches the subsequence of captured logs with the specified metadata key. */
  public LogsSubject withMetadataKey(String key) {
    return check("withMetadataKey('%s')", key)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> hasMetadataKey(e, key)));
  }

  /** Asserts about the number of matched logs. */
  public IntegerSubject matchCount() {
    return check("matchCount()").that(logs.size());
  }

  /** Asserts that only one log entry is matched, and returns it. */
  public LogEntry getOnlyMatch() {
    Fact error = Fact.simpleFact("was expected to match exactly one log");
    if (logs.isEmpty()) {
      failWithoutActual(error, Fact.simpleFact("but was empty"));
    } else if (logs.size() > 1) {
      failWithActual(error);
    }
    return logs.get(0);
  }

  /**
   * Returns the Nth matched log entry, asserting that there are at least {@code (n + 1)} matched
   * log entries.
   */
  public LogEntry getMatch(int n) {
    checkArgument(n >= 0, "Match index must not be negative");
    if (n >= logs.size()) {
      failWithActual(Fact.simpleFact("expected at least " + (n + 1) + " matching logs"));
    }
    return logs.get(0);
  }

  /** Returns the list of current matches without making any assertions. */
  public ImmutableList<LogEntry> getAllMatches() {
    return logs;
  }

  /** Allows a following assertion to be applied to every matched log entry. */
  public AllLogsSubject always() {
    return check("always()").about(AllLogsSubject.logs("all", Stream::allMatch)).that(logs);
  }

  /** Allows a following assertion to be applied to every matched log entry in a negative sense. */
  public AllLogsSubject never() {
    return check("never()").about(AllLogsSubject.logs("no", Stream::noneMatch)).that(logs);
  }

  /** Assertions to be applied to all matched log entries. */
  public static class AllLogsSubject extends Subject {
    private static Factory<AllLogsSubject, ImmutableList<LogEntry>> logs(
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

    /**
     * Asserts that matched log entries have the given level.
     *
     * <p>Note that there is currently no assertion for "below" or "above" a given level, since this
     * is better handled by filtering the matched log entries before applying an assertion.
     */
    public void haveLevel(LevelClass level) {
      if (!op.test(logs.stream(), e -> e.levelClass() == level)) {
        failWithActual(label + " matched logs were expected be at level", level);
      }
    }

    /** Asserts that matched log entries have a cause of the specified type. */
    public void haveCause(Class<? extends Throwable> clazz) {
      if (!op.test(logs.stream(), e -> hasCause(e, clazz))) {
        failWithActual(label + " matched logs were expected have cause of type", clazz.getName());
      }
    }

    /** Asserts that matched log entries have the specified metadata key-value pair. */
    public void haveMetadata(String key, Object value) {
      if (!op.test(logs.stream(), e -> hasMetadata(e, key, value))) {
        failWithActual(label + " matched logs were expected have metadata", key + "=" + value);
      }
    }

    /** Asserts that matched log entries have the specified metadata key. */
    public void haveMetadataKey(String key) {
      if (!op.test(logs.stream(), e -> hasMetadataKey(e, key))) {
        failWithActual(label + " matched logs were expected have metadata key", key);
      }
    }
  }

  private ImmutableList<LogEntry> filterAfter(LogEntry entry) {
    int index = logs.indexOf(entry);
    checkArgument(index >= 0, "Provided log entry does not exist: %s", entry);
    return logs.subList(index + 1, logs.size());
  }

  private ImmutableList<LogEntry> filterBefore(LogEntry entry) {
    int index = logs.indexOf(entry);
    checkArgument(index >= 0, "Provided log entry does not exist: %s", entry);
    return logs.subList(0, index);
  }

  private static boolean hasCause(LogEntry e, Class<? extends Throwable> clazz) {
    return clazz.isInstance(e.cause());
  }

  private static boolean hasMetadataKey(LogEntry e, String key) {
    return e.metadata().containsKey(key);
  }

  private static boolean hasMetadata(LogEntry e, String key, Object value) {
    return e.metadata().getOrDefault(key, ImmutableList.of()).contains(value);
  }

  // A one line snippet aimed at identifying a log entry as part of an error message.
  // It is going to be very common that this is show for errors in assertions near to
  // code which extracted this log entry, so it should be unlikely to be ambiguous.
  private static String quoteIfString(Object value) {
    return value instanceof String ? "'" + value + "'" : String.valueOf(value);
  }
}
