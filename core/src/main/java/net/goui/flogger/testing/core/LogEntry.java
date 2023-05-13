package net.goui.flogger.testing.core;

import static java.lang.Character.isHighSurrogate;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.goui.flogger.testing.LevelClass;
import org.checkerframework.checker.nullness.qual.Nullable;

@AutoValue
public abstract class LogEntry {

  public static LogEntry of(
      @Nullable String className,
      @Nullable String methodName,
      String levelName,
      LevelClass levelClass,
      String message,
      ImmutableMap<String, ImmutableList<Object>> metadata,
      Throwable cause) {
    return new AutoValue_LogEntry(
        className != null ? className : "unknown",
        methodName != null ? methodName : "unknown",
        levelName,
        levelClass,
        message,
        metadata,
        cause);
  }

  public abstract String className();

  public abstract String methodName();

  public abstract String levelName();

  public abstract LevelClass levelClass();

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
    String logSiteString = className();
    logSiteString = logSiteString.substring(logSiteString.lastIndexOf('.') + 1);
    logSiteString += "#" + methodName();
    // For JDK level show just "FINE", for Log4J show "DEBUG(FINE)" etc.
    String levelString = levelName();
    if (!levelString.equals(levelClass().name())) {
      levelString += "(" + levelClass().name() + ")";
    }
    String messageSnippet = shortSnippet(getMessage());
    String causeStr = getCause() != null ? ", cause=" + getCause().getClass().getSimpleName() : "";
    String metadataStr = !getMetadata().isEmpty() ? ", context=" + getMetadata() : "";
    return logSiteString
        + "@"
        + levelString
        + ": \""
        + messageSnippet
        + "\""
        + causeStr
        + metadataStr;
  }

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
