package net.goui.flogger.testing.core;

import com.google.auto.value.AutoValue;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.logging.Level;

@AutoValue
public abstract class CapturedLog {
  public enum LevelCheck { ABOVE, COMPATIBLE, BELOW }

  public interface LevelTester {
    int test(Level level);
  }

  static CapturedLog of(LevelTester levelTester, String levelName, String message) {
    return new AutoValue_CapturedLog(levelTester, levelName, message);
  }

  abstract LevelTester levelTester();
  abstract String levelName();

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
