package net.goui.flogger.testing.core.truth;

import com.google.auto.value.AutoValue;
import java.util.logging.Level;

@AutoValue
public abstract class LogEntry {
  public enum LevelCheck { ABOVE, COMPATIBLE, BELOW }

  public interface LevelTester {
    int test(Level level);
  }

  public static LogEntry of(LevelTester levelTester, String levelName, String message) {
    return new AutoValue_LogEntry(levelTester, levelName, message);
  }

  abstract LevelTester levelTester();
  public abstract String levelName();

  public abstract String getMessage();

  @Override
  public final String toString() {
    return String.format("Log{%s: %s}", levelName(), getMessage());
  }

  public final LevelCheck checkLevel(Level level) {
    switch (Integer.signum(levelTester().test(level))) {
      case -1: return LevelCheck.BELOW;
      case 0: return LevelCheck.COMPATIBLE;
      case 1: return LevelCheck.ABOVE;
      default: throw new AssertionError("signum only returns {-1, 0, 1}");
    }
  }
}
