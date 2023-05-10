package net.goui.flogger.testing.core.truth;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;

@AutoValue
public abstract class LogEntry {
  public enum LevelCheck {
    ABOVE,
    COMPATIBLE,
    BELOW
  }

  public interface LevelTester {
    int test(Level level);
  }

  public static LogEntry of(
      LevelTester levelTester,
      String levelName,
      String message,
      ImmutableMap<String, ImmutableList<Object>> metadata,
      Throwable cause) {
    return new AutoValue_LogEntry(levelTester, levelName, message, metadata, cause);
  }

  abstract LevelTester levelTester();

  public abstract String levelName();

  public abstract String getMessage();

  public abstract ImmutableMap<String, ImmutableList<Object>> getMetadata();

  @Nullable
  public abstract Throwable getCause();

  @Override
  public final String toString() {
    return String.format(
        "Log{%s: message='%s', cause='%s', metadata='%s'}",
        levelName(), getMessage(), getCause(), getMetadata());
  }

  public final LevelCheck checkLevel(Level level) {
    switch (Integer.signum(levelTester().test(level))) {
      case -1:
        return LevelCheck.BELOW;
      case 0:
        return LevelCheck.COMPATIBLE;
      case 1:
        return LevelCheck.ABOVE;
      default:
        throw new AssertionError("signum only returns {-1, 0, 1}");
    }
  }
}
