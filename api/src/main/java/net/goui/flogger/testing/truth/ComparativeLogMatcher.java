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

  private ComparativeLogMatcher adapted(String label, Predicate<LogEntry> predicate) {
    return new ComparativeLogMatcher(
        getLabel() + "." + label,
        target,
        (logs, target) -> getFilter().apply(logs).filter(predicate));
  }
}
