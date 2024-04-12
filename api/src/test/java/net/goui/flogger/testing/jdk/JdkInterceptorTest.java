/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.jdk;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.LogInterceptor.Recorder;
import net.goui.flogger.testing.api.RecorderSpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JdkInterceptorTest {
  private static final String TEST_ID = "Test ID";

  private static Logger logger(String name) {
    Logger logger = Logger.getLogger(name);
    logger.setLevel(Level.INFO);
    return logger;
  }

  @Test
  public void testInterceptorScope() {
    Logger jdkLogger = logger("foo.bar.Baz");
    Logger childLogger = logger("foo.bar.Baz.Child");
    Logger parentLogger = logger("foo.bar");
    Logger siblingLogger = logger("foo.bar.Sibling");

    LogInterceptor interceptor = JdkInterceptor.create();
    List<LogEntry> logged = new ArrayList<>();
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", LevelClass.INFO, logged::add)) {
      assertThat(logged).isEmpty();
      jdkLogger.info("Log message");
      childLogger.info("Child message");
      parentLogger.info("Parent message");
      siblingLogger.info("Sibling message");

      assertThat(logged).hasSize(2);
      assertThat(logged.get(0).message()).isEqualTo("Log message");
      assertThat(logged.get(1).message()).isEqualTo("Child message");
    }
    jdkLogger.info("After test!");
    assertThat(logged).hasSize(2);
  }

  @Test
  public void testMetadata_allTypes() {
    Logger jdkLogger = logger("foo.bar.Baz");
    LogInterceptor interceptor = JdkInterceptor.create();
    List<LogEntry> logged = new ArrayList<>();
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", LevelClass.INFO, logged::add)) {
      jdkLogger.warning("Message [CONTEXT foo=true ]");
      jdkLogger.warning("Message [CONTEXT bar=1234 ]");
      jdkLogger.warning("Message [CONTEXT bar=1.23e6 ]");
      jdkLogger.warning("Message [CONTEXT baz=\"\\tline1\\n\\t\\\"line2\\\"\" ]");
      jdkLogger.warning("Message [CONTEXT key ]");

      assertThat(logged.get(0).metadata()).containsExactly("foo", List.of(true));
      assertThat(logged.get(1).metadata()).containsExactly("bar", List.of(1234L));
      assertThat(logged.get(2).metadata()).containsExactly("bar", List.of(1.23e6D));
      assertThat(logged.get(3).metadata()).containsExactly("baz", List.of("\tline1\n\t\"line2\""));
      assertThat(logged.get(4).metadata()).containsExactly("key", List.of());
    }
  }

  @Test
  public void testMetadata_multipleValues() {
    Logger jdkLogger = logger("foo.bar.Baz");
    LogInterceptor interceptor = JdkInterceptor.create();
    List<LogEntry> logged = new ArrayList<>();
    try (Recorder recorder = interceptor.attachTo("foo.bar.Baz", LevelClass.INFO, logged::add)) {
      jdkLogger.warning(
          "Message [CONTEXT foo=true foo=1234 foo=1.23e6 foo=\"\\tline1\\n\\t\\\"line2\\\"\" ]");

      assertThat(logged.get(0).metadata())
          .containsExactly("foo", List.of(true, 1234L, 1.23e6D, "\tline1\n\t\"line2\""));
    }
  }

  @Test
  public void testTestIdFiltering() {
    String className = "foo.bar.Baz";
    Logger jdkLogger = logger("foo.bar.Baz");

    LogInterceptor interceptor = JdkInterceptor.create();
    List<LogEntry> logged = new ArrayList<>();
    RecorderSpec spec = RecorderSpec.of("foo.bar.Baz", LevelClass.INFO);
    try (Recorder recorder =
        interceptor.attachTo(
            "foo.bar.Baz", spec.getMinLevel(), spec.wrapCollector(logged::add, TEST_ID))) {
      assertThat(logged).isEmpty();

      jdkLogger.logp(Level.INFO, className, "methodName", "No test ID");
      jdkLogger.logp(
          Level.INFO,
          className,
          "methodName",
          "Valid test ID [CONTEXT test_id=\"" + TEST_ID + "\" ]");
      jdkLogger.logp(
          Level.INFO,
          className,
          "methodName",
          "Multiple test IDs [CONTEXT test_id=\"" + TEST_ID + "\" test_id=\"xxx\" ]");
      jdkLogger.logp(
          Level.INFO, className, "methodName", "Unmatched test ID [CONTEXT test_id=\"xxx\" ]");

      assertThat(logged).hasSize(3);
      assertThat(logged.get(0).message()).isEqualTo("No test ID");
      assertThat(logged.get(1).message()).isEqualTo("Valid test ID");
      assertThat(logged.get(2).message()).isEqualTo("Multiple test IDs");
    }
  }
}
