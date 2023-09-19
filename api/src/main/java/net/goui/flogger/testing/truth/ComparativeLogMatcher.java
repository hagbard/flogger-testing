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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.goui.flogger.testing.LogEntry;

/** Log matcher with reference to a specific target log entry against which matching can occur. */
final class ComparativeLogMatcher extends LogMatcher {
  private final LogEntry target;

  // Instances are created from static factory methods in LogMatcher.
  ComparativeLogMatcher(
      String label,
      LogEntry target,
      BiFunction<Stream<LogEntry>, LogEntry, Stream<LogEntry>> filter) {
    super(label, logs -> filter.apply(logs, target));
    this.target = checkNotNull(target);
  }

  /**
   * Returns a derived matcher which additionally requires that log entries be from the same thread
   * as the target or this matcher.
   */
  public ComparativeLogMatcher inSameThread() {
    return adapted("inSameThread()", e -> e.hasSameThreadAs(target));
  }

  /**
   * Returns a derived matcher which additionally requires that log entries be from the same outer
   * class as the target of this matcher.
   *
   * <p>This is the default recommended way to test for logs "from the same source file" as other
   * logs, and while you can test for exact class matching (distinguishing nested and inner classes)
   * or even exact method matching, these risk making your tests more brittle than necessary.
   *
   * <p>Log entries with unknown class names are never considered equal (even to themselves).
   */
  public ComparativeLogMatcher fromSameOuterClass() {
    return adapted("fromSameOuterClass()", e -> LogFilters.hasSameOuterClass(e, target));
  }

  /**
   * Returns a derived matcher which additionally requires that log entries be from the same class
   * as the target of this matcher. Log statements executed in lambdas or anonymous inner classes
   * are considered to come from the first non-synthetic (i.e. named) containing class, which can
   * itself be a nested or inner class.
   *
   * <p>Warning: Using this matcher may make some tests more brittle than necessary in the face of
   * normal refactoring, so you may prefer to use {@link #fromSameOuterClass()} instead.
   *
   * <p>Log entries with unknown class names are never considered equal (even to themselves).
   */
  public ComparativeLogMatcher fromSameClass() {
    return adapted("fromSameClass()", e -> LogFilters.hasSameClass(e, target));
  }

  /**
   * Returns a derived matcher which additionally requires that log entries be from the same class
   * and method as the target of this matcher. Log statements executed in lambdas or anonymous inner
   * classes are considered to come from the first non-synthetic (i.e. named) containing method.
   *
   * <p>Warning: Using this matcher may make some tests more brittle than necessary in the face of
   * normal refactoring, so you may prefer to use {@link #fromSameOuterClass()} instead.
   *
   * <p>Log entries with unknown class/method names are never considered equal (even to themselves).
   */
  public ComparativeLogMatcher fromSameMethod() {
    return adapted("fromSameMethod()", e -> LogFilters.hasSameClassAndMethod(e, target));
  }

  private ComparativeLogMatcher adapted(String label, Predicate<LogEntry> predicate) {
    return new ComparativeLogMatcher(
        getLabel() + "." + label,
        target,
        (logs, target) -> getFilter().apply(logs).filter(predicate));
  }
}
