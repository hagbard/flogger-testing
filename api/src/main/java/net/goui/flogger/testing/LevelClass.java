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

import java.util.logging.Level;

/**
 * Represents the smallest equivalence class of log levels which all backends should be able to
 * support.
 *
 * <p>Since assertions are carried out on logged values, it is important that the test API expresses
 * its contract in a way that is compatible with all supported logging backends. In particular,
 * testing the log statements:
 *
 * <pre>{@code
 * logger.atFine().log("Detailed message ...");
 * logger.atFinest().log("Very detailed message ...");
 * }</pre>
 *
 * <p>Cannot allow the use of {@link Level#FINE} and {@link Level#FINEST}, because some backends do
 * not distinguish these levels. And assertions like:
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
 * <p>Note that the names here reflect the JDK log level names to best match the calling code being
 * testing, and enum entries are explicitly ordered in ascending severity so that {@code
 * x.compareTo(y)} works as expected.
 */
public enum LevelClass {
  /**
   * A level class for the most detailed logs, often disabled unless fine-grained debug information
   * is required.
   *
   * <p>All remaining log levels below {@link #FINE}.
   *
   * <p>Note that in the JDK, this includes {@link Level#FINER}, and prevents the testing of
   * differences between {@code FINEST} and {@code FINER} logs. However, users should probably not
   * being trying to write tests which care about this distinction as they are likely to be brittle.
   */
  FINEST(Level.FINEST),
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
  FINE(Level.FINE),
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
  INFO(Level.INFO),
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
  WARNING(Level.WARNING),
  /**
   * The highest level class, indicating serious and urgent problems.
   *
   * <ul>
   *   <li>JDK level {@code SEVERE} and above.
   *   <li>Log4J level {@code ERROR} and above, including {@code FATAL}(†).
   *   <li>Android log level {@code ERROR}, including {@code ASSERT}(†).
   * </ul>
   *
   * <p>(†) Since Flogger does not emit these log levels, it should not affect testing. Of course if
   * this API were used to test non-Flogger logs, this might become important.
   */
  SEVERE(Level.SEVERE);

  private final Level jdkLevel;

  LevelClass(Level jdkLevel) {
    this.jdkLevel = jdkLevel;
  }

  public Level toJdkLogLevel() {
    return jdkLevel;
  }

  /** An alias for {@link #FINEST}, for users who wish to use Log4J style names in tests. */
  public static final LevelClass TRACE = FINEST;

  /** An alias for {@link #FINE}, for users who wish to use Log4J style names in tests. */
  public static final LevelClass DEBUG = FINE;

  /** An alias for {@link #SEVERE}, for users who wish to use Log4J style names in tests. */
  public static final LevelClass ERROR = SEVERE;
}
