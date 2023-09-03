package net.goui.flogger.testing;

import static java.lang.Character.isHighSurrogate;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Representation of a captured log entry for testing via the Truth based API.
 *
 * <p>This class is suitable for representing log entries for testing, in which some information may
 * be lost. The choices made in the design of this class exist to allow it to represent logged
 * entries from several common logging systems (especially the JDK logger and Log4J).
 *
 * <p>Where data is not preserved exactly it should not be an issue, since logging tests should not
 * be overly precise anyway. Any such issues are noted in the JavaDoc.
 *
 * <p>Instances of this class are only expected to be created as part of a {@code LogInjector} and
 * passed to the appropriate subject class. Users of the Truth API are never expected to need to
 * make log entries themselves.
 */
@AutoValue
public abstract class LogEntry {
  /**
   * @param className the optional class name of the log site for this entry (not necessarily the
   *     name of the logger which logged it). This is primarily informational, to aid the user in
   *     finding a log site for a failing test. If the class name cannot be determined, passing
   *     {@code null} results in {@code "<unknown>"} being used.
   * @param methodName the optional plain method name (no signature) of the log-site for this entry.
   *     This is primarily informational, to aid the user in finding a log site for a failing test.
   *     If the method name cannot be determined, passing {@code null} results in {@code
   *     "<unknown>"} being used.
   * @param levelName the name of the log level used by the underlying logging system to log this
   *     entry (for example, in Log4J this could be {@code "DEBUG"} rather than {@code "FINE"}).
   * @param levelClass the normalized equivalence class for the log level, calculated from the
   *     underlying log level.
   * @param timestamp the timestamp for this entry.
   * @param message the log message (possibly processed to remove metadata or other additional
   *     formatting). This must contain at least the original formatted log message, but can be
   *     longer.
   * @param metadata key/value metadata extracted from the underlying log structure. This can be
   *     coded in the formatted message (e.g. {@code "[CONTEXT foo="bar" ]"}, or as part of a
   *     structured log entry). If this metadata is parsed from the underlying logged message, it
   *     should also be removed from it. Metadata values can only have 4 types (Boolean, Long,
   *     Double and String) and it is up to the extractor to preserve types accordingly and document
   *     its behaviour.
   * @param cause an optional {@code Throwable} representing a "cause" for the log statement.
   *     Conceptually this is just metadata, but since it's so commonly pulled out as a separate
   *     specific concept by common logging libraries, it's kept separate in this API.
   * @return a LogEntry suitable for asserting on by the Truth API.
   */
  public static LogEntry of(
      @Nullable String className,
      @Nullable String methodName,
      String levelName,
      LevelClass levelClass,
      Instant timestamp,
      String message,
      ImmutableMap<String, ImmutableList<Object>> metadata,
      @Nullable Throwable cause) {
    return new AutoValue_LogEntry(
        className != null ? className : "<unknown>",
        methodName != null ? methodName : "<unknown>",
        levelName,
        levelClass,
        timestamp,
        message,
        metadata,
        cause);
  }

  /** Returns the log site's class name, or {@code "<unknown>"} if it could not be determined. */
  public abstract String className();

  /** Returns the log site's method name, or {@code "<unknown>"} if it could not be determined. */
  public abstract String methodName();

  /**
   * Returns the name of the underlying log level (e.g. {@code "DEBUG"} rather than {@code "FINE"}
   * in Log4J.
   */
  public abstract String levelName();

  /** Returns the equivalence class of the log level (see {@link LevelClass}). */
  public abstract LevelClass levelClass();

  /** The timestamp of the log message (used to assert relative order). */
  public abstract Instant timeStamp();

  /**
   * Return the original log message (minus any metadata). Note that this is allowed to contain
   * additional formatting, but must always contain at least the original formatted log message.
   */
  public abstract String message();

  /**
   * Returns the key/value metadata extracted from the underlying log entry. Note that metadata:
   *
   * <ul>
   *   <li>Can have multiple entries per key.
   *   <li>Can have a key without any values (to indicate key presence as a "tag").
   *   <li>Only allows values to be of type Boolean, Long, Double or String.
   * </ul>
   *
   * Type information in metadata is important and, for example, if a log statement specifies a
   * metadata value as an {@code int}, it must be losslessly represented here as a {@code Long}.
   * This permits log assertions to test exact values for primitive types.
   */
  public abstract ImmutableMap<String, ImmutableList<Object>> metadata();

  /** Returns the associated "cause" of this log entry (if present). */
  @Nullable
  public abstract Throwable cause();

  /**
   * A deliberately concise representation of the log, intended for showing in test failures without
   * causing too much "spammy" output, while retaining enough information to identify the log
   * statement from which it came. In particular, this string representation may truncate log
   * messages and only show summaries of other values.
   *
   * <p><em>This representation is designed to be human readable only, and can change at any
   * time.</em>
   */
  @Override
  public final String toString() {
    String logSiteString = className();
    logSiteString = logSiteString.substring(logSiteString.lastIndexOf('.') + 1);
    logSiteString += "#" + methodName();
    String causeStr = cause() != null ? ", cause=" + cause().getClass().getSimpleName() : "";
    String metadataStr = !metadata().isEmpty() ? ", context=" + metadata() : "";
    return logSiteString + "@" + snippet() + causeStr + metadataStr;
  }

  /**
   * A one line snippet aimed at identifying a log entry as part of an error message.
   *
   * <p>It is going to be very common that this is shown for errors in assertions near to the test
   * code which generated this log entry, so it should be unlikely to be ambiguous to the user.
   */
  public String snippet() {
    // For JDK level show just "FINE", for Log4J show "DEBUG(FINE)" etc.
    String levelString = levelName();
    if (!levelString.equals(levelClass().name())) {
      levelString += "(" + levelClass().name() + ")";
    }
    return levelString + ": \"" + shortSnippet(message()) + "\"";
  }

  // Rough and ready trim of the log message to no more than 30 chars.
  private static String shortSnippet(String message) {
    int splitIndex = message.indexOf('\n');
    if (splitIndex == -1) {
      splitIndex = message.length();
    }
    splitIndex = Math.min(30, splitIndex);
    if (splitIndex == message.length()) {
      return message;
    }
    if (isHighSurrogate(message.charAt(splitIndex - 1))) {
      splitIndex--;
    }
    // Definitely truncating!
    return message.substring(0, splitIndex) + "...";
  }
}
