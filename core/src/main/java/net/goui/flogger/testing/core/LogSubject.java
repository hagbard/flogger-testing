package net.goui.flogger.testing.core;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import java.util.logging.Level;
import net.goui.flogger.testing.core.CapturedLog.LevelCheck;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class LogSubject extends Subject {
  private final CapturedLog actual;

  /**
   * Constructor for use by subclasses. If you want to create an instance of this class itself, call
   * {@link Subject#check(String, Object...) check(...)}{@code .that(actual)}.
   */
  private LogSubject(FailureMetadata metadata, @Nullable CapturedLog actual) {
    super(metadata, actual);
    this.actual = actual;
  }

  public static Factory<LogSubject, CapturedLog> logs() {
    return LogSubject::new;
  }

  public static LogSubject assertThat(@Nullable CapturedLog actual) {
    return assertAbout(logs()).that(actual);
  }

  public StringSubject message() {
    return check("message()").that(actual.getMessage());
  }

  public void hasLevelCompatibleWith(Level level) {
    if (actual.checkLevel(level) != LevelCheck.COMPATIBLE) {
      failWithoutActual(
          Fact.simpleFact(
              "logged level '" + actual.levelName() + "' is not compatible with [" + level + "]"));
    }
  }

  public void hasLevelAbove(Level level) {
    if (actual.checkLevel(level) != LevelCheck.ABOVE) {
      failWithoutActual(
          Fact.simpleFact(
              "logged level '" + actual.levelName() + "' was not above [" + level + "]"));
    }
  }

  public void hasLevelBelow(Level level) {
    if (actual.checkLevel(level) != LevelCheck.BELOW) {
      failWithoutActual(
          Fact.simpleFact(
              "logged level '" + actual.levelName() + "' was not below [" + level + "]"));
    }
  }
}
