/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.truth;

import static net.goui.flogger.testing.truth.LogFilters.containsAllFragmentsInOrder;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;

/** Logs testing API for making assertions about every item in a sequence of matched log entries. */
public final class MatchedLogsSubject extends Subject {
  static Factory<MatchedLogsSubject, ImmutableList<LogEntry>> allMatchedLogs() {
    return matched("all", Stream::allMatch);
  }

  static Factory<MatchedLogsSubject, ImmutableList<LogEntry>> noMatchedLogs() {
    return matched("no", Stream::noneMatch);
  }

  private static Factory<MatchedLogsSubject, ImmutableList<LogEntry>> matched(
      String label, BiPredicate<Stream<LogEntry>, Predicate<? super LogEntry>> op) {
    return (subject, logs) -> new MatchedLogsSubject(subject, logs, label, op);
  }

  private final ImmutableList<LogEntry> logs;
  private final String label;
  private final BiPredicate<Stream<LogEntry>, Predicate<? super LogEntry>> op;

  private MatchedLogsSubject(
      FailureMetadata metadata,
      ImmutableList<LogEntry> logs,
      String label,
      BiPredicate<Stream<LogEntry>, Predicate<? super LogEntry>> op) {
    super(metadata, logs);
    this.logs = logs;
    this.label = label;
    this.op = op;
  }

  /**
   * Asserts that all matched log entries have messages containing all the specified text fragments
   * in the given order. Matches are case-sensitive and do not delimit word boundaries.
   *
   * <p>A log entry is only matched if its message contains all the given fragments in the given
   * order (not including cases where overlap occurs). For example:
   *
   * <ul>
   *   <li>"Hello World" is matched by {@code ("Hello", "World")}, but not {@code ("World",
   *       "Hello")}.
   *   <li>"Foo Bar Baz" is matched by {@code ("Ba", "Ba")}, but not {@code ("Fo", "Fo")}.
   *   <li>"Information" is not matched by {@code ("Inform", "formation")}.
   * </ul>
   */
  public void haveMessageContaining(String fragment, String... rest) {
    if (!op.test(logs.stream(), e -> containsAllFragmentsInOrder(e.message(), fragment, rest))) {
      failWithActual(label + " matched logs were expected to contain", fragment);
    }
  }

  /**
   * Asserts that matched log entries have messages containing a match to the specified regular
   * expression.
   */
  public void haveMessageMatching(String regex) {
    Predicate<String> regexPredicate = Pattern.compile(regex).asPredicate();
    if (!op.test(logs.stream(), e -> regexPredicate.test(e.message()))) {
      failWithActual(label + " matched logs were expected to match", regex);
    }
  }

  /** Asserts that matched log entries are at the given level. */
  public void haveLevel(LevelClass level) {
    if (!op.test(logs.stream(), e -> e.levelClass() == level)) {
      failWithActual(label + " matched logs were expected to be at level", level);
    }
  }

  /** Asserts that matched log entries are above the given level. */
  public void haveLevelGreaterThan(LevelClass level) {
    if (!op.test(logs.stream(), e -> e.levelClass().compareTo(level) > 0)) {
      failWithActual(label + " matched logs were expected to be at level", level);
    }
  }

  /** Asserts that matched log entries are at or above the given level. */
  public void haveLevelAtLeast(LevelClass level) {
    if (!op.test(logs.stream(), e -> e.levelClass().compareTo(level) >= 0)) {
      failWithActual(label + " matched logs were expected to be at level", level);
    }
  }

  /** Asserts that matched log entries are below the given level. */
  public void haveLevelLessThan(LevelClass level) {
    if (!op.test(logs.stream(), e -> e.levelClass().compareTo(level) < 0)) {
      failWithActual(label + " matched logs were expected to be at level", level);
    }
  }

  /** Asserts that matched log entries are at or below the given level. */
  public void haveLevelAtMost(LevelClass level) {
    if (!op.test(logs.stream(), e -> e.levelClass().compareTo(level) <= 0)) {
      failWithActual(label + " matched logs were expected to be at level", level);
    }
  }

  /** Asserts that matched log entries have a cause of the specified type. */
  public void haveCause(Class<? extends Throwable> clazz) {
    if (!op.test(logs.stream(), e -> clazz.isInstance(e.cause()))) {
      failWithActual(
          label + " matched logs were expected to have a cause of type", clazz.getName());
    }
  }

  /** Asserts that matched log entries have a cause of any type. */
  public void haveCause() {
    if (!op.test(logs.stream(), e -> e.cause() != null)) {
      failWithActual(Fact.simpleFact(label + " matched logs were expected to have a cause"));
    }
  }

  /** Asserts that matched log entries have the specified metadata key-value pair. */
  public void haveMetadata(String key, @Nullable Object value) {
    haveMetadataImpl(key, value);
  }

  /** Asserts that matched log entries have the specified metadata key-value pair. */
  public void haveMetadata(String key, long value) {
    haveMetadataImpl(key, value);
  }

  /** Asserts that matched log entries have the specified metadata key-value pair. */
  public void haveMetadata(String key, double value) {
    haveMetadataImpl(key, value);
  }

  /** Asserts that matched log entries have the specified metadata key-value pair. */
  public void haveMetadata(String key, boolean value) {
    haveMetadataImpl(key, value);
  }

  private void haveMetadataImpl(String key, @Nullable Object value) {
    if (!op.test(logs.stream(), e -> e.hasMetadata(key, value))) {
      failWithActual(label + " matched logs were expected have metadata", key + "=" + value);
    }
  }

  /** Asserts that matched log entries have the specified metadata key. */
  public void haveMetadataKey(String key) {
    if (!op.test(logs.stream(), e -> e.hasMetadataKey(key))) {
      failWithActual(label + " matched logs were expected have metadata key", key);
    }
  }
}
