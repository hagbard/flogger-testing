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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogEntryTest {
  private static final Instant TIMESTAMP = Instant.now();
  private static final Object THREAD_ID = "<dummy>";

  @Test
  public void testAllArguments() {
    ImmutableMap<String, ImmutableList<Object>> metadata =
        ImmutableMap.of("foo", ImmutableList.of("bar"));
    Throwable cause = new RuntimeException();
    LogEntry entry =
        LogEntry.of(
            "<class>",
            "<method>",
            "<info>",
            LevelClass.INFO,
            TIMESTAMP,
            THREAD_ID,
            "log message",
            metadata,
            cause);
    assertThat(entry.className()).isEqualTo("<class>");
    assertThat(entry.methodName()).isEqualTo("<method>");
    assertThat(entry.levelName()).isEqualTo("<info>");
    assertThat(entry.levelClass()).isEqualTo(LevelClass.INFO);
    assertThat(entry.timeStamp()).isEqualTo(TIMESTAMP);
    assertThat(entry.message()).isEqualTo("log message");
    assertThat(entry.metadata()).isEqualTo(metadata);
    assertThat(entry.cause()).isEqualTo(cause);
  }

  @Test
  public void testOptionalArguments() {
    LogEntry entry =
        LogEntry.of(
            null,
            null,
            "<info>",
            LevelClass.INFO,
            TIMESTAMP,
            THREAD_ID,
            "log message",
            ImmutableMap.of(),
            null);
    assertThat(entry.className()).isEqualTo("<unknown>");
    assertThat(entry.methodName()).isEqualTo("<unknown>");
    assertThat(entry.metadata()).isEmpty();
    assertThat(entry.cause()).isNull();
  }

  @Test
  public void testHasMetadata() {
    LogEntry e =
        LogEntry.of(
            null,
            null,
            "<info>",
            LevelClass.INFO,
            TIMESTAMP,
            THREAD_ID,
            "<unused>",
            ImmutableMap.of(
                "key", values("key", "false", "42.0", 123456L, 54321D, Math.PI, true),
                "empty", values()),
            null);

    // Passes with explicit string value, but not a boolean, integer or double.
    assertThat(e.hasMetadata("key", "false")).isTrue();
    assertThat(e.hasMetadata("key", "42.0")).isTrue();
    assertThat(e.hasMetadata("key", false)).isFalse();
    assertThat(e.hasMetadata("key", 42)).isFalse();
    assertThat(e.hasMetadata("key", 42.0)).isFalse();

    // Passes based on the toString() representation of the instance, not its exact value.
    Object falsey =
        new Object() {
          @Override
          public String toString() {
            return "false";
          }
        };
    assertThat(e.hasMetadata("key", falsey)).isTrue();

    // Passes even though the value in the metadata is a long.
    assertThat(e.hasMetadata("key", 123456)).isTrue();

    // Passes even though the value in the metadata is a double (it is an exact numerical match).
    assertThat(e.hasMetadata("key", 54321)).isTrue();

    // Passes using BigDecimal and BigInteger.
    assertThat(e.hasMetadata("key", BigDecimal.valueOf(123456.0))).isTrue();
    assertThat(e.hasMetadata("key", BigInteger.valueOf(54321))).isTrue();

    // Passes with 10 digits of precision, but fails with 9.
    assertThat(e.hasMetadata("key", 3.1415926536)).isTrue();
    assertThat(e.hasMetadata("key", 3.141592654)).isFalse();

    // Passes with boolean value, but not with string representation.
    assertThat(e.hasMetadata("key", true)).isTrue();
    assertThat(e.hasMetadata("key", "true")).isFalse();

    // Test metadata key containment (even without values, keys can exist in metadata).
    assertThat(e.hasMetadataKey("key")).isTrue();
    assertThat(e.hasMetadataKey("empty")).isTrue();
    assertThat(e.hasMetadataKey("nope")).isFalse();
  }

  @Test
  public void testInferredClassAndMethodNames() {
    // Pre Java 11, lambdas appeared in the class name.
    LogEntry lambdaInClassName =
        LogEntry.of(
            "ClassName$$Lambda$123/xyz",
            "methodName",
            "<info>",
            LevelClass.INFO,
            TIMESTAMP,
            THREAD_ID,
            "<message>",
            ImmutableMap.of(),
            null);
    assertThat(lambdaInClassName.className()).isEqualTo("ClassName");
    assertThat(lambdaInClassName.methodName()).isEqualTo("methodName");

    // Recently lambdas appeared in the method name (more sensible).
    LogEntry lambdaInMethodName =
        LogEntry.of(
            "ClassName",
            "lambda$methodName$123",
            "<info>",
            LevelClass.INFO,
            TIMESTAMP,
            THREAD_ID,
            "<message>",
            ImmutableMap.of(),
            null);
    assertThat(lambdaInMethodName.className()).isEqualTo("ClassName");
    assertThat(lambdaInMethodName.methodName()).isEqualTo("methodName");
  }

  @Test
  public void testInferredClassAndMethodNames_unexpected() {
    // Unexpected trailing class/method names should work, but cause warnings.
    LogEntry lambdaInClassName =
        LogEntry.of(
            "ClassName$$unexpected",
            "methodName",
            "<info>",
            LevelClass.INFO,
            TIMESTAMP,
            THREAD_ID,
            "<message>",
            ImmutableMap.of(),
            null);
    assertThat(lambdaInClassName.className()).isEqualTo("ClassName");
    assertThat(lambdaInClassName.methodName()).isEqualTo("methodName");

    LogEntry lambdaInMethodName =
        LogEntry.of(
            "ClassName",
            "unexpected$methodName",
            "<info>",
            LevelClass.INFO,
            TIMESTAMP,
            THREAD_ID,
            "<message>",
            ImmutableMap.of(),
            null);
    assertThat(lambdaInMethodName.className()).isEqualTo("ClassName");
    assertThat(lambdaInMethodName.methodName()).isEqualTo("methodName");
  }

  @Test
  public void testSnippet() {
    // Shorter messages are emitted as is.
    LogEntry entry = snippetEntry("log message");
    assertThat(entry.snippet()).isEqualTo("<info>(INFO): \"log message\"");

    // Multiline messages use only the first line (but show truncation).
    LogEntry multiLine = snippetEntry("log message\nsecond line");
    assertThat(multiLine.snippet()).isEqualTo("<info>(INFO): \"log message...\"");

    // Handle other newline representations.
    LogEntry windows = snippetEntry("log message\r\nsecond line");
    assertThat(windows.snippet()).isEqualTo("<info>(INFO): \"log message...\"");

    // Longer messages are trimmed.
    LogEntry longMessage = snippetEntry("log message long enough to need to be trimmed");
    assertThat(longMessage.snippet())
        .isEqualTo("<info>(INFO): \"log message long enough to nee...\"");

    // Edge case where surrogate pair straddles the max message length (30 chars).
    LogEntry trailingSurrogate = snippetEntry("<10 chars><10 chars><9 chars>\uD801\uDC37");
    assertThat(trailingSurrogate.snippet())
        .isEqualTo("<info>(INFO): \"<10 chars><10 chars><9 chars>...\"");
  }

  private static LogEntry snippetEntry(String message) {
    return LogEntry.of(
        "<unused in snippet>",
        "<unused in snippet>",
        "<info>",
        LevelClass.INFO,
        TIMESTAMP,
        THREAD_ID,
        message,
        ImmutableMap.of(),
        null);
  }

  @Test
  public void testToString() {
    ImmutableMap<String, ImmutableList<Object>> metadata =
        ImmutableMap.of("foo", ImmutableList.of("bar"));
    Throwable cause = new RuntimeException();
    LogEntry entry =
        LogEntry.of(
            "<class>",
            "<method>",
            "<info>",
            LevelClass.INFO,
            TIMESTAMP,
            THREAD_ID,
            "log message",
            metadata,
            cause);
    assertThat(entry.toString()).contains("<class>#<method>@<info>(INFO)");
    assertThat(entry.toString()).contains("log message");
    assertThat(entry.toString()).contains("RuntimeException");
    assertThat(entry.toString()).contains("foo=[bar]");
  }

  private static ImmutableList<Object> values(Object... values) {
    checkArgument(
        Arrays.stream(values)
            .allMatch(
                v ->
                    v instanceof Long
                        || v instanceof Double
                        || v instanceof Boolean
                        || v instanceof String),
        "Metadata values expected to only be Long, Double, Boolean or String");
    return ImmutableList.copyOf(values);
  }
}
