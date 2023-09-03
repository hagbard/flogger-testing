package net.goui.flogger.testing.truth;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.regex.Pattern;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import org.checkerframework.checker.nullness.qual.Nullable;

/** */
public final class LogSubject extends Subject {
  private final LogEntry logEntry;

  private LogSubject(FailureMetadata metadata, @Nullable LogEntry logEntry) {
    super(metadata, logEntry);
    this.logEntry = logEntry;
  }

  private LogEntry entry() {
    return checkNotNull(logEntry);
  }

  public static Factory<LogSubject, LogEntry> logs() {
    return LogSubject::new;
  }

  public static LogSubject assertThat(LogEntry logEntry) {
    return assertAbout(logs()).that(logEntry);
  }

  /**
   * Asserts that the log entry/entries under test have a message which contains a specific
   * substring. Tests should assert only important information in a log message and avoid testing
   * for content which is prone to change through normal refactoring.
   */
  public void hasMessageContaining(String substring) {
    if (!entry().message().contains(substring)) {
      failWithActual("expected to contain substring", substring);
    }
  }

  /**
   * Asserts that the log entry/entries under test have a message which contains a substring which
   * matches a given regular expression. Tests should assert only important information in a log
   * message and avoid testing for content which is prone to change through normal refactoring.
   */
  public void hasMessageMatching(String regex) {
    Pattern pattern = Pattern.compile(regex);
    check("message()").that(entry().message()).containsMatch(regex);
  }

  /** Asserts that the log entry/entries under test were logged at the given level class. */
  public void isAtLevel(LevelClass level) {
    check("level()").that(entry().levelClass()).isEqualTo(level);
  }

  /**
   * Asserts that the log entry/entries under test have an associated "cause" of the specified type.
   * Often it is sufficient to just test that a log contains a "cause", rather than asserting
   * something specific about it as these are often values created in code outside the control of
   * the code under test.
   */
  public void hasCause(Class<? extends Throwable> type) {
    check("cause()").that(entry().cause()).isInstanceOf(type);
  }

  /**
   * Asserts that the log entry/entries under test have metadata with the given key/value pair.
   * Metadata has no inherent order, and tests should only look for the metadata they care about.
   */
  public void hasMetadata(String key, @Nullable String value) {
    hasMetadataImpl(key, value);
  }

  /**
   * Asserts that the log entry/entries under test have metadata with the given key/value pair.
   * Metadata has no inherent order, and tests should only look for the metadata they care about.
   */
  public void hasMetadata(String key, long value) {
    hasMetadataImpl(key, value);
  }

  /**
   * Asserts that the log entry/entries under test have metadata with the given key/value pair.
   * Metadata has no inherent order, and tests should only look for the metadata they care about.
   */
  public void hasMetadata(String key, double value) {
    hasMetadataImpl(key, value);
  }

  /**
   * Asserts that the log entry/entries under test have metadata with the given key/value pair.
   * Metadata has no inherent order, and tests should only look for the metadata they care about.
   */
  public void hasMetadata(String key, boolean value) {
    hasMetadataImpl(key, value);
  }

  /**
   * Asserts that the log entry/entries under test have metadata with the given key. Metadata has no
   * inherent order, and tests should only look for the metadata they care about.
   */
  public void hasMetadataKey(String key) {
    hasMetadataImpl(key, null);
  }

  private void hasMetadataImpl(String key, @Nullable Object value) {
    check("metadata()")
        .withMessage("log metadata did not contain key %s", key)
        .that(entry().metadata())
        .containsKey(key);
    if (value != null) {
      ImmutableList<Object> values = checkNotNull(entry().metadata().get(key));
      String valueStr = value instanceof String ? "\"" + values + "\"" : value.toString();
      check("metadata().get(\"%s\")", key)
          .withMessage("log metadata did not contain entry {%s: %s}", key, valueStr)
          .that(values)
          .contains(value);
    }
  }
}
