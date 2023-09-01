package net.goui.flogger.testing.truth;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;

import javax.annotation.CheckReturnValue;

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

  public LogsSubject afterLog(LogEntry entry) {
    return check("afterLog('%s')", snippet(entry))
        .about(LogsSubject.logs())
        .that(filterAfter(entry));
  }

  private ImmutableList<LogEntry> filterAfter(LogEntry entry) {
    int index = logs.indexOf(entry);
    checkArgument(index >= 0, "Provided log entry does not exist: %s", entry);
    return logs.subList(index + 1, logs.size());
  }

  public LogsSubject beforeLog(LogEntry entry) {
    return check("beforeLog('%s')", snippet(entry))
        .about(LogsSubject.logs())
        .that(filterBefore(entry));
  }

  private ImmutableList<LogEntry> filterBefore(LogEntry entry) {
    int index = logs.indexOf(entry);
    checkArgument(index >= 0, "Provided log entry does not exist: %s", entry);
    return logs.subList(0, index);
  }

  public LogsSubject withMessageContaining(String fragment) {
    return check("withMessageContaining('%s')", fragment)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.message().contains(fragment)));
  }

  public LogsSubject withMessageMatching(String regex) {
    Predicate<String> regexPredicate = Pattern.compile(regex).asPredicate();
    return check("withMessageMatching('%s')", regex)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> regexPredicate.test(e.message())));
  }

  public LogsSubject atLevel(LevelClass level) {
    return check("atLevel('%s')", level)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.levelClass() == level));
  }

  public LogsSubject aboveLevel(LevelClass level) {
    return check("aboveLevel('%s')", level)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) > 0));
  }

  public LogsSubject atOrAboveLevel(LevelClass level) {
    return check("atOrAboveLevel('%s')", level)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) >= 0));
  }

  public LogsSubject belowLevel(LevelClass level) {
    return check("belowLevel('%s')", level)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) < 0));
  }

  public LogsSubject atOrBelowLevel(LevelClass level) {
    return check("atOrBelowLevel('%s')", level)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> e.levelClass().compareTo(level) <= 0));
  }

  public LogsSubject withCause(Class<? extends Throwable> clazz) {
    return check("withCause(%s)", clazz.getName())
        .about(LogsSubject.logs())
        .that(filter(logs, e -> hasCause(e, clazz)));
  }

  public LogsSubject withMetadata(String key, Object value) {
    return check("withMetadata('%s', %s)", key, quoteIfString(value))
        .about(LogsSubject.logs())
        .that(filter(logs, e -> hasMetadata(e, key, value)));
  }

  public LogsSubject withMetadataKey(String key) {
    return check("withMetadataKey('%s')", key)
        .about(LogsSubject.logs())
        .that(filter(logs, e -> hasMetadataKey(e, key)));
  }

  public IntegerSubject matchCount() {
    return check("matchCount()").that(logs.size());
  }

  public LogEntry get() {
    Fact error = Fact.simpleFact("was expected to match exactly one log");
    if (logs.isEmpty()) {
      failWithoutActual(error, Fact.simpleFact("but was empty"));
    } else if (logs.size() > 1) {
      failWithActual(error);
    }
    return logs.get(0);
  }

  public LogEntry firstMatch() {
    if (logs.isEmpty()) {
      failWithoutActual(
          Fact.simpleFact("was expected to match at least one log"),
          Fact.simpleFact("but was empty"));
    }
    return logs.get(0);
  }

  public AllLogsSubject always() {
    return check("always()").about(AllLogsSubject.logs("all", Stream::allMatch)).that(logs);
  }

  public AllLogsSubject never() {
    return check("never()").about(AllLogsSubject.logs("no", Stream::noneMatch)).that(logs);
  }

  public static class AllLogsSubject extends Subject {
    private final ImmutableList<LogEntry> logs;
    private final String label;
    private final BiPredicate<Stream<LogEntry>, Predicate<? super LogEntry>> op;

    protected AllLogsSubject(
        FailureMetadata metadata,
        ImmutableList<LogEntry> logs,
        String label,
        BiPredicate<Stream<LogEntry>, Predicate<? super LogEntry>> op) {
      super(metadata, logs);
      this.logs = logs;
      this.label = label;
      this.op = op;
    }

    static Factory<AllLogsSubject, ImmutableList<LogEntry>> logs(
        String label, BiPredicate<Stream<LogEntry>, Predicate<? super LogEntry>> op) {
      return (subject, logs) -> new AllLogsSubject(subject, logs, label, op);
    }

    public void haveMessageContaining(String fragment) {
      if (!op.test(logs.stream(), e -> e.message().contains(fragment))) {
        failWithActual(label + " matched logs were expected to contain", fragment);
      }
    }

    public void haveMessageMatching(String regex) {
      Predicate<String> regexPredicate = Pattern.compile(regex).asPredicate();
      if (!op.test(logs.stream(), e -> regexPredicate.test(e.message()))) {
        failWithActual(label + " matched logs were expected to match", regex);
      }
    }

    public void haveLevel(LevelClass level) {
      if (!op.test(logs.stream(), e -> e.levelClass() == level)) {
        failWithActual(label + " matched logs were expected be at level", level);
      }
    }

    public void haveCause(Class<? extends Throwable> clazz) {
      if (!op.test(logs.stream(), e -> hasCause(e, clazz))) {
        failWithActual(label + " matched logs were expected have cause of type", clazz.getName());
      }
    }

    public void haveMetadata(String key, Object value) {
      if (!op.test(logs.stream(), e -> hasMetadata(e, key, value))) {
        failWithActual(label + " matched logs were expected have metadata", key + "=" + value);
      }
    }

    public void haveMetadataKey(String key) {
      if (!op.test(logs.stream(), e -> hasMetadataKey(e, key))) {
        failWithActual(label + " matched logs were expected have metadata key", key);
      }
    }
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
  private static String snippet(LogEntry entry) {
    // Rough and ready trim of the log message to no more than 30 code points.
    String msg = entry.message();
    int cpLen = msg.codePointCount(0, msg.length());
    if (cpLen > 30) {
      msg = msg.substring(0, msg.offsetByCodePoints(0, 30));
      msg = CharMatcher.whitespace().trimTrailingFrom(msg) + "...";
    }
    return entry.levelClass() + ": " + msg;
  }

  // A one line snippet aimed at identifying a log entry as part of an error message.
  // It is going to be very common that this is show for errors in assertions near to
  // code which extracted this log entry, so it should be unlikely to be ambiguous.
  private static String quoteIfString(Object value) {
    return value instanceof String ? "'" + value + "'" : String.valueOf(value);
  }
}
