/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.api;

import static com.google.common.collect.Comparators.min;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;

/**
 * Handles the mapping between logging class names and underlying logger backend names to ensure log
 * entries can be captured according to the user-visible log capture specifications.
 *
 * <p>If no backend name mapping exists, and all backend names are just the logging class name, this
 * class has little effect, and one {@code RecorderSpec} instance will exist for each log capture
 * specification (class name and level).
 *
 * <p>However, if the configured backend name mapping can shared many logging classes with a single
 * backend, then it is necessary to create a "merged" filter which can be attached to the backend,
 * but which remembers the (possibly very different) log capture specifications which apply to it.
 *
 * <p>On top of this, it must also be possible to filter out log entries which do not have the same
 * test ID (if this is specified).
 *
 * <p>Overall the predicate for filtering log entries for capture looks like:
 *
 * <pre>{@code
 * test_id_predicate && (capture_predicate_1 || capture_predicate_2 || ... || capture_predicate_N)
 * }</pre>
 *
 * when each {@code capture_predicate_n} is derived from a user facing capture specification for a
 * class or package.
 */
public final class RecorderSpec {
  /**
   * Converts a map of individual log capture specifications (class/package name -> level) to a
   * merged map of recorder specifications (backend name -> merged filter predicate).
   *
   * <p>This provides a single filter we can add to each backend which preserves all the log capture
   * specifications associated with it.
   *
   * @param levelMap map of log capture specifications using class/package names.
   * @param backendNameFn mapping function from class/package name to backend name.
   * @return a map, keyed by backend name, of log capture predicates.
   */
  public static Map<String, RecorderSpec> getRecorderSpecs(
      Map<String, LevelClass> levelMap, UnaryOperator<String> backendNameFn) {
    Map<String, RecorderSpec> specMap = new LinkedHashMap<>();
    // Note: compute() will use a null spec the first time a backend name is added to the map.
    levelMap.forEach(
        (name, level) ->
            specMap.compute(backendNameFn.apply(name), (key, spec) -> create(spec, name, level)));
    return specMap;
  }

  /** Creates a single recorder specification (useful for testing). */
  public static RecorderSpec of(String name, LevelClass level) {
    return create(null, name, level);
  }

  private static RecorderSpec create(RecorderSpec spec, String name, LevelClass level) {
    Predicate<LogEntry> capturePredicate =
        e -> checkLoggingClass(e, name) && level.compareTo(e.levelClass()) <= 0;
    return spec == null
        ? new RecorderSpec(level, capturePredicate)
        : new RecorderSpec(min(spec.minLevel, level), spec.capturePredicate.or(capturePredicate));
  }

  private final LevelClass minLevel;
  private final Predicate<LogEntry> capturePredicate;

  private RecorderSpec(LevelClass minLevel, Predicate<LogEntry> capturePredicate) {
    this.minLevel = minLevel;
    this.capturePredicate = capturePredicate;
  }

  /**
   * Wrap a collector to filter log entries by the user facing log capture specifications from which
   * this recorder was made.
   *
   * @param collector to be filtered
   * @param testId additional test ID which (if not empty) must match a metadata in the log entries.
   * @return a filtered wrapper over the given collector.
   */
  public Consumer<LogEntry> applyFilter(Consumer<LogEntry> collector, String testId) {
    return e -> {
      // Apply the test ID predicate before considering the (possibly merged) capture predicate.
      if (checkTestId(e, testId) && capturePredicate.test(e)) {
        collector.accept(e);
      }
    };
  }

  /**
   * Returns the minimum log level across all log capture specifications from which this recorder
   * was made.
   */
  public LevelClass getMinLevel() {
    return minLevel;
  }

  private static boolean checkTestId(LogEntry entry, String testId) {
    return testId.isEmpty()
        || !entry.hasMetadataKey("test_id")
        || entry.hasMetadata("test_id", testId);
  }

  private static boolean checkLoggingClass(LogEntry entry, String name) {
    String loggingClass = entry.className();
    if (!loggingClass.startsWith(name)) {
      return false;
    }
    if (loggingClass.length() == name.length()) {
      return true;
    }
    // loggingClass must be longer than name, but might not be a "child".
    char nextChar = loggingClass.charAt(name.length());
    return nextChar == '$' || nextChar == '.';
  }
}
