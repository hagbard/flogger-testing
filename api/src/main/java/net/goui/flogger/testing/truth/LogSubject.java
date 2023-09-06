/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

 This program and the accompanying materials are made available under the terms of the
 Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

 SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.truth;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.*;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Logs testing API for making assertions about a single log entry. */
public final class LogSubject extends Subject {
  private final LogEntry logEntry;

  private LogSubject(FailureMetadata metadata, @Nullable LogEntry logEntry) {
    super(metadata, logEntry);
    this.logEntry = logEntry;
  }

  private LogEntry entry() {
    return checkNotNull(logEntry);
  }

  public static Factory<LogSubject, LogEntry> logs() {
    return LogSubject::new;
  }

  public static LogSubject assertThat(LogEntry logEntry) {
    return assertAbout(logs()).that(logEntry);
  }

  /**
   * Asserts that a log entry has a message which contains a specific substring. Tests should assert
   * only important information in a log message and avoid testing for content which is prone to
   * change through normal refactoring.
   */
  public void hasMessageContaining(String substring) {
    checkArgument(!substring.isEmpty(), "message fragment cannot be empty");
    message().contains(substring);
  }

  /**
   * Asserts that a log entry has a message which contains a substring which matches a given regular
   * expression. Tests should assert only important information in a log message and avoid testing
   * for content which is prone to change through normal refactoring.
   */
  public void hasMessageMatching(String regex) {
    checkArgument(!regex.isEmpty(), "message regex cannot be empty");
    message().containsMatch(regex);
  }

  /**
   * Returns a {@link StringSubject} for the message of this log entry. This is available for rare
   * cases where more complex testing is required. In general, prefer calling the level assertions
   * directly on this class whenever possible.
   */
  public StringSubject message() {
    return check("message()").that(entry().message());
  }

  /** Asserts that the level of the log under test is equal to the given level. */
  public void hasLevel(LevelClass level) {
    checkNotNull(level, "log level cannot be null");
    level().isEqualTo(level);
  }

  /** Asserts that the level of the log under test is above the given level. */
  public void hasLevelGreaterThan(LevelClass level) {
    level().isGreaterThan(level);
  }

  /** Asserts that the level of the log under test is at or above the given level. */
  public void hasLevelAtLeast(LevelClass level) {
    level().isAtLeast(level);
  }

  /** Asserts that the level of the log under test is below the given level. */
  public void hasLevelLessThan(LevelClass level) {
    level().isLessThan(level);
  }

  /** Asserts that the level of the log under test is at or below the given level. */
  public void hasLevelAtMost(LevelClass level) {
    level().isAtMost(level);
  }

  /**
   * Returns a {@link ComparableSubject} for the level of this log entry. This is available for rare
   * cases where a range of levels needs to be checked. In general, prefer calling the level
   * assertions directly on this class whenever possible.
   */
  public ComparableSubject<LevelClass> level() {
    return check("level()").that(entry().levelClass());
  }

  /**
   * Asserts that a log entry has an associated "cause" of the specified type. Often it is
   * sufficient to just test that a log contains a "cause", rather than asserting something specific
   * about it as these are often values created in code outside the control of the code under test.
   */
  public void hasCause(Class<? extends Throwable> type) {
    checkNotNull(type, "cause type must not be null. Did you mean \"cause().isNull()\"?");
    if (!type.isInstance(entry().cause())) {
      failWithActual("log cause was not of expected type", type.getName());
    }
  }

  /**
   * Returns a {@link ThrowableSubject} for the cause of this log entry. This is useful when you
   * need to test more than just the type of a cause.
   */
  public ThrowableSubject cause() {
    return check("cause()").that(entry().cause());
  }

  /**
   * Asserts that a log entry has metadata with the given key/value pair. Metadata has no inherent
   * order, and tests should only look for the metadata they care about, rather than checking that
   * the entry has exactly some set of metadata.
   *
   * <p>Note: Unlike other attributes of {@code LogEntry}, there is no {@code MetadataSubject} and
   * it's not a good idea to test the metadata map directly.
   */
  public void hasMetadata(String key, Object value) {
    if (!entry().hasMetadata(key, value)) {
      failWithActual(
          "log metadata did not contain key-value pair", key + "=" + quoteIfString(value));
    }
  }

  /**
   * Asserts that a log entry has metadata with the given key. Metadata has no inherent order, and
   * tests should only look for the metadata they care about.
   */
  public void hasMetadataKey(String key) {
    if (!entry().hasMetadataKey(key)) {
      failWithActual("log metadata did not contain key", key);
    }
  }

  private static String quoteIfString(Object value) {
    return value instanceof String ? "'" + value + "'" : String.valueOf(value);
  }
}
