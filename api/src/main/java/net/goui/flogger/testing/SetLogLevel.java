/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A repeatable annotation which lets users override and modify the log default log level map
 * initialized in the test fixture.
 *
 * <p>Exactly one of {@code target}, {@code name} or {@code scope} must be set, along with a desired
 * {@code level}.
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

  /** A semantic scope to define which class or package is affected. */
  Scope scope() default Scope.UNDEFINED;

  /** The new log level. */
  LevelClass level();

  @Retention(RetentionPolicy.RUNTIME)
  @Target(value = ElementType.METHOD)
  @interface Map {
    SetLogLevel[] value() default {};
  }

  /**
   * A semantic scope to define which class or package is affected.
   *
   * <p>This has the same role as test fixture methods like {@code forClassUnderTest()} and {@code
   * forPackageUnderTest()}.
   */
  enum Scope {
    UNDEFINED,
    /** Applies the associated log level to the inferred class under test. */
    CLASS_UNDER_TEST,
    /** Applies the associated log level to the inferred package under test. */
    PACKAGE_UNDER_TEST
  }
}
