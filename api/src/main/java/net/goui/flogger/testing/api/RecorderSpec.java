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

import com.google.common.flogger.backend.Platform;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;

public final class RecorderSpec {
  static Map<String, RecorderSpec> getRecorderSpecs(Map<String, LevelClass> levelMap) {
    Map<String, RecorderSpec> specMap = new HashMap<>();
    levelMap.forEach(
        (name, level) ->
            specMap.compute(getBackendName(name), (key, spec) -> create(spec, name, level)));
    return specMap;
  }

  public static RecorderSpec of(String name, LevelClass level) {
    return create(null, name, level);
  }

  private static RecorderSpec create(RecorderSpec spec, String name, LevelClass level) {
    Predicate<LogEntry> predicate =
        e -> checkLoggingClass(e, name) && level.compareTo(e.levelClass()) <= 0;
    return spec == null
        ? new RecorderSpec(level, predicate)
        : new RecorderSpec(min(spec.minLevel, level), spec.shouldCapture.or(predicate));
  }

  private final LevelClass minLevel;
  private final Predicate<LogEntry> shouldCapture;

  private RecorderSpec(LevelClass minLevel, Predicate<LogEntry> shouldCapture) {
    this.minLevel = minLevel;
    this.shouldCapture = shouldCapture;
  }

  public Consumer<LogEntry> wrapCollector(Consumer<LogEntry> collector, String testId) {
    return e -> {
      if (checkTestId(e, testId) && shouldCapture.test(e)) {
        collector.accept(e);
      }
    };
  }

  public LevelClass getMinLevel() {
    return minLevel;
  }

  private static String getBackendName(String loggingClassName) {
    return Platform.getBackend(loggingClassName).getLoggerName();
  }

  private static boolean checkTestId(LogEntry entry, String testId) {
    return testId.isEmpty()
        || !entry.hasMetadataKey("test_id")
        || entry.hasMetadata("test_id", testId);
  }

  private static boolean checkLoggingClass(LogEntry entry, String name) {
    String loggingClass = entry.className();
    boolean isPrefix = loggingClass.startsWith(name);
    if (isPrefix && loggingClass.length() == name.length()) {
      return true;
    }
    // loggingClass must be longer than name.
    char nextChar = loggingClass.charAt(name.length());
    return nextChar == '$' || nextChar == '.';
  }
}
