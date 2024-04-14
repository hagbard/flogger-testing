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

import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.LevelClass.FINE;
import static net.goui.flogger.testing.LevelClass.INFO;
import static net.goui.flogger.testing.LevelClass.WARNING;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import junit.framework.TestCase;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RecorderSpecTest extends TestCase {
  @Test
  public void testOf() {
    RecorderSpec spec = RecorderSpec.of("foo.bar", INFO);
    // Unless merged, specs just contain the single level value.
    assertThat(spec.getMinLevel()).isEqualTo(INFO);

    // Don't test the test ID here, just rely on matching class and level.
    List<String> messages = new ArrayList<>();
    Consumer<LogEntry> collector = spec.applyFilter(e -> messages.add(e.message()), "");

    collector.accept(log("foo.bar", INFO, "<matched exactly>"));
    collector.accept(log("foo.bar", WARNING, "<matched with higher level>"));
    collector.accept(log("foo.bar", FINE, "<unmatched by lower level>"));
    collector.accept(log("foo.bar.baz.Class", INFO, "<matched with sub package>"));
    collector.accept(log("foo.baz.Class", INFO, "<unmatched by class name>"));

    assertThat(messages)
        .containsExactly(
            "<matched exactly>", "<matched with higher level>", "<matched with sub package>")
        .inOrder();
  }

  @Test
  public void testGetRecorderSpecs() {
    Map<String, LevelClass> levelMap =
        Map.of(
            "foo.bar.First",
            WARNING,
            "foo.bar.Second",
            INFO,
            "foo.bar.Third",
            FINE,
            "bar.foo.OtherPackage",
            INFO);

    Map<String, RecorderSpec> specs =
        RecorderSpec.getRecorderSpecs(levelMap, RecorderSpecTest::toParentPackage);
    assertThat(specs.keySet()).containsExactly("foo.bar", "bar.foo");
    RecorderSpec merged = specs.get("foo.bar");

    assertThat(merged.getMinLevel()).isEqualTo(FINE);

    List<String> messages = new ArrayList<>();
    Consumer<LogEntry> collector = merged.applyFilter(e -> messages.add(e.message()), "");

    collector.accept(log("foo.bar.First", WARNING, "<matched 1>"));
    collector.accept(log("foo.bar.First", INFO, "<unmatched by level>"));
    // But the second logger matches at a different level.
    collector.accept(log("foo.bar.Second", INFO, "<matched 2>"));
    collector.accept(log("foo.bar.Secondly", INFO, "<unmatched by name>"));
    // And the third logger matches for both class and inner/nested class.
    collector.accept(log("foo.bar.Third", FINE, "<matched 3>"));
    collector.accept(log("foo.bar.Third$Forth", FINE, "<matched 4>"));

    assertThat(messages)
        .containsExactly("<matched 1>", "<matched 2>", "<matched 3>", "<matched 4>")
        .inOrder();
  }

  @Test
  public void testApplyFilter_withTestId() {
    RecorderSpec spec = RecorderSpec.of("foo.bar", INFO);

    // Don't test the test ID here, just rely on matching class and level.
    List<String> messages = new ArrayList<>();
    Consumer<LogEntry> collector = spec.applyFilter(e -> messages.add(e.message()), "<TEST_ID>");

    collector.accept(log("foo.bar", INFO, "<matched without test ID>"));
    collector.accept(logWithTestId("foo.bar", INFO, "<matched with test ID>", "<TEST_ID>"));
    collector.accept(logWithTestId("foo.bar", INFO, "<unmatched bad test ID>", "<BAD_ID>"));

    assertThat(messages)
        .containsExactly("<matched without test ID>", "<matched with test ID>")
        .inOrder();
  }

  private static String toParentPackage(String classOrPackage) {
    int dot = classOrPackage.lastIndexOf('.');
    return dot >= 0 ? classOrPackage.substring(0, dot) : "";
  }

  private static LogEntry log(String className, LevelClass level, String message) {
    return LogEntry.of(
        className,
        "<method>",
        level.name(),
        level,
        Instant.now(),
        123L /*threadId*/,
        message,
        ImmutableMap.of(),
        null /*cause*/);
  }

  private static LogEntry logWithTestId(
      String className, LevelClass level, String message, String testId) {
    return LogEntry.of(
        className,
        "<method>",
        level.name(),
        level,
        Instant.now(),
        123L /*threadId*/,
        message,
        ImmutableMap.of("test_id", ImmutableList.of(testId)),
        null /*cause*/);
  }
}
