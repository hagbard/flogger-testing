package net.goui.flogger.testing.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.goui.flogger.testing.LevelClass;

/**
 * A repeatable annotation which lets users override and modify the log default log level map
 * initialized in the test fixture.
 *
 * <p>When a log level is overridden for a class or package, it only applies to exactly that class
 * or package. For example, if the test fixture were set up with {@code foo.bar.Baz => INFO} and a
 * test was annotated with {@code @SetLogLevel(name = "foo.bar", FINE)}, then the class {@code
 * foo.bar.Baz} would still be unaffected, but other classes in that package would have their test
 * level changed.
 *
 * <p>This mechanism is needed because the logging test fixture installs it handlers before the test
 * begins, so there's no possibility of modifying it from within test code. However, an annotation
 * on the test method can be read during the test setup.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = ElementType.METHOD)
@Repeatable(value = SetLogLevel.Map.class)
public @interface SetLogLevel {
  /** The target class for which the log level should be overridden. */
  Class<?> target() default Object.class;

  /** The target class or package name for which the log level should be overridden. */
  String name() default "";

  /** The new log level. */
  LevelClass level();

  @Retention(RetentionPolicy.RUNTIME)
  @Target(value = ElementType.METHOD)
  @interface Map {
    SetLogLevel[] value() default {};
  }
}
