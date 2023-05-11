package net.goui.flogger.testing.core;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.logging.Level;
import net.goui.flogger.testing.core.AutoValue_LogEntry;
import org.checkerframework.checker.nullness.qual.Nullable;

@AutoValue
public abstract class LogEntry {
  /**
   * Represents the smallest equivalence class of log levels which all backends should be able to
   * support.
   *
   * <p>Since assertions are carried out on logged values, it is important that the test API
   * expresses its contract in a way compatible with all backends. In particular, testing the log
   * statements:
   *
   * <pre>{@code
   * logger.atFine().log("Detailed message ...");
   * logger.atFinest().log("Very detailed message ...");
   * }</pre>
   *
   * <p>Cannot allow the use of {@link Level#FINE} and {@link Level#FINEST}, because some backends
   * do not distinguish these levels and assertions like.
   *
   * <pre>{@code
   * assertThat(logs).everyLog().above(Level.FINEST).hasSomeProperty(...);
   * }</pre>
   *
   * <p>Would be brittle if you moved to a backend which could not distinguish between {@link
   * Level#FINE}, {@link Level#FINER} and {@link Level#FINEST}.
   *
   * <p>However, limiting the available level classes is not unreasonable because the finest log
   * levels are not semantically important for testing and tests which specify levels too precisely
   * are going to be brittle in the face of simple refactoring.
   *
   * <p>Note that the names here reflect the JDK log level names to best match the calling code
   * being testing, and enum entries are explicitly ordered in ascending severity so that {@code
   * x.compareTo(y)} works as expected.
   */
  public enum LevelClass {
    /**
     * A level class for the most detailed logs, often disabled unless fine-grained debug
     * information is required.
     *
     * <p>All remaining log levels below {@link #FINE}.
     *
     * <p>Note that in the JDK, this includes {@link Level#FINER}, and prevents the testing of
     * differences between {@code FINEST} and {@code FINER} logs. However, users should probably not
     * being trying to write tests which care about this distinction as they are likely to be
     * brittle.
     */
    FINEST,
    /**
     * A level class indicating logs suitable for high level debugging and are typically disabled
     * under normal circumstances.
     *
     * <p>Log levels below {@link #INFO} but:
     *
     * <ul>
     *   <li>JDK level {@code FINE} and above (including {@code CONFIG}).
     *   <li>Log4J level {@code DEBUG} and above.
     *   <li>Android log level {@code DEBUG}.
     * </ul>
     */
    FINE,
    /**
     * A level class indicating informational logs which require no action but are typically always
     * enabled.
     *
     * <p>Log levels below {@link #WARNING} but:
     *
     * <ul>
     *   <li>JDK level {@code INFO} and above.
     *   <li>Log4J level {@code INFO} and above.
     *   <li>Android log level {@code INFO}.
     * </ul>
     */
    INFO,
    /**
     * A level class indicating warnings that are actionable, but not as urgent as {@link #SEVERE}.
     *
     * <p>Log levels below {@link #SEVERE} but:
     *
     * <ul>
     *   <li>JDK level {@code WARNING} and above.
     *   <li>Log4J level {@code WARN} and above.
     *   <li>Android log level {@code WARN}.
     * </ul>
     */
    WARNING,
    /**
     * The highest level class, indicating serious and urgent problems.
     *
     * <ul>
     *   <li>JDK level {@code SEVERE} and above.
     *   <li>Log4J level {@code ERROR} and above, including {@code FATAL}(†).
     *   <li>Android log level {@code ERROR}, including {@code ASSERT}(†).
     * </ul>
     *
     * <p>(†) Since Flogger does not emit these log levels, it should not affect testing. Of course
     * if this API were used to test non-Flogger logs, this might become important.
     */
    SEVERE,
  }

  public static LogEntry of(
      LevelClass levelClass,
      String levelName,
      String message,
      ImmutableMap<String, ImmutableList<Object>> metadata,
      Throwable cause) {
    return new AutoValue_LogEntry(levelClass, levelName, message, metadata, cause);
  }

  public abstract LevelClass levelClass();

  public abstract String levelName();

  public abstract String getMessage();

  public abstract ImmutableMap<String, ImmutableList<Object>> getMetadata();

  @Nullable
  public abstract Throwable getCause();

  /**
   * A deliberately concise representation of the log, intended for showing in test failures without
   * causing too much "spammy" output, while retaining enough information to identify the log
   * statement from which it came..
   */
  @Override
  public final String toString() {
    // For JDK level show just "FINE", for Log4J show "DEBUG(FINE)" etc.
    String levelString = levelName();
    if (!levelString.equals(levelClass().name())) {
      levelString += "(" + levelClass().name() + ")";
    }
    String causeStr = getCause() != null ? ", cause=" + getCause().getClass().getSimpleName() : "";
    String metadataStr = !getMetadata().isEmpty() ? ", context=" + getMetadata() : "";
    return "Log{" + levelString + ": '" + getMessage() + "'" + causeStr + metadataStr + "}";
  }
}
