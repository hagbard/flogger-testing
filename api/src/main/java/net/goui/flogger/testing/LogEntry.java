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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Character.isHighSurrogate;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Immutable representation of a captured log entry for testing via the Truth based API.
 *
 * <p>This class is suitable for representing log entries for testing, in which some information may
 * be lost. The choices made in the design of this class exist to allow it to represent logged
 * entries from several common logging systems (especially the JDK logger and Log4J).
 *
 * <p>Where data is not preserved exactly it should not be an issue, since logging tests should not
 * be overly precise anyway. Any such issues are noted in the JavaDoc.
 *
 * <p>Instances of this class are only expected to be created as part of a {@code LogInjector} and
 * passed to the appropriate subject class. Users of the Truth API are never expected to need to
 * make log entries themselves.
 */
@AutoValue
public abstract class LogEntry {
  private static final int TRIMMED_MESSAGE_LENGTH = 30;
  private static final AtomicBoolean hasWarnedUnknownMethodNamingConvention = new AtomicBoolean();
  private static final AtomicBoolean hasWarnedUnknownClassNamingConvention = new AtomicBoolean();

  /**
   * @param className the optional class name of the log site for this entry (not necessarily the
   *     name of the logger which logged it). This is primarily informational, to aid the user in
   *     finding a log site for a failing test. If the class name cannot be determined, passing
   *     {@code null} results in {@code "<unknown>"} being used.
   * @param methodName the optional plain method name (no signature) of the log-site for this entry.
   *     This is primarily informational, to aid the user in finding a log site for a failing test.
   *     If the method name cannot be determined, passing {@code null} results in {@code
   *     "<unknown>"} being used.
   * @param levelName the name of the log level used by the underlying logging system to log this
   *     entry (for example, in Log4J this could be {@code "DEBUG"} rather than {@code "FINE"}).
   * @param levelClass the normalized equivalence class for the log level, calculated from the
   *     underlying log level.
   * @param timestamp the timestamp for this entry.
   * @param message the log message (possibly processed to remove metadata or other additional
   *     formatting). This must contain at least the original formatted log message, but can be
   *     longer.
   * @param metadata key/value metadata extracted from the underlying log structure. This can be
   *     coded in the formatted message (e.g. {@code "[CONTEXT foo="bar" ]"}, or as part of a
   *     structured log entry). If this metadata is parsed from the underlying logged message, it
   *     should also be removed from it. Metadata values can only have 4 types (Boolean, Long,
   *     Double and String) and it is up to the extractor to preserve types accordingly and document
   *     its behaviour.
   * @param cause an optional {@code Throwable} representing a "cause" for the log statement.
   *     Conceptually this is just metadata, but since it's so commonly pulled out as a separate
   *     specific concept by common logging libraries, it's kept separate in this API.
   * @return a LogEntry suitable for asserting on by the Truth API.
   */
  public static LogEntry of(
      @Nullable String className,
      @Nullable String methodName,
      String levelName,
      LevelClass levelClass,
      Instant timestamp,
      Object threadId,
      String message,
      ImmutableMap<String, ImmutableList<Object>> metadata,
      @Nullable Throwable cause) {
    return new AutoValue_LogEntry(
        className != null ? inferClassNameForTesting(className) : "<unknown>",
        methodName != null ? inferMethodNameForTesting(methodName) : "<unknown>",
        levelName,
        levelClass,
        timestamp,
        threadId,
        message,
        metadata,
        cause);
  }

  /**
   * Returns the log site's class name, or {@code "<unknown>"} if it could not be determined.
   *
   * <p>If a log statement occurs in an anonymous inner class or lambda, then the inferred class
   * name is that of the containing (explicitly named) class, which may still be an inner or nested
   * class. This avoids brittle tests where simple refactorings change the exact (system defined)
   * class name.
   */
  public abstract String className();

  /**
   * Returns the log site's method name, or {@code "<unknown>"} if it could not be determined.
   *
   * <p>If a log statement occurs in an anonymous inner class or lambda, then the inferred method
   * name is that of the containing (explicitly named) method. This avoids brittle tests where
   * simple refactorings change the exact (system defined) method name.
   */
  public abstract String methodName();

  /**
   * Returns the name of the underlying log level (e.g. {@code "DEBUG"} rather than {@code "FINE"}
   * in Log4J). This is deliberately not public, since it is backend specific and is only used as
   * part of debug strings.
   */
  abstract String levelName();

  /** Returns the equivalence class of the log level (see {@link LevelClass}). */
  public abstract LevelClass levelClass();

  /** The timestamp of the log message (used to assert relative order). */
  public abstract Instant timeStamp();

  /**
   * A mostly unique identifier for the thread in which logging occurred. This is deliberately not
   * public, since we only need to compare instances for detecting log entries from the same thread
   * and the precise instance used for thread identification may change over time.
   */
  abstract Object threadId();

  /**
   * Return the original log message (minus any metadata). Note that this is allowed to contain
   * additional formatting, but must always contain at least the original formatted log message.
   */
  public abstract String message();

  /**
   * Returns the key/value metadata extracted from the underlying log entry. Note that metadata:
   *
   * <ul>
   *   <li>Can have multiple entries per key.
   *   <li>Can have a key without any values (to indicate key presence as a "tag").
   *   <li>Only allows values to be of type Boolean, Long, Double or String.
   * </ul>
   *
   * Type information in metadata is important and, for example, if a log statement specifies a
   * metadata value as an {@code int}, it must be losslessly represented here as a {@code Long}.
   * This permits log assertions to test exact values for primitive types.
   */
  public abstract ImmutableMap<String, ImmutableList<Object>> metadata();

  /** Returns the associated "cause" of this log entry (if present). */
  @Nullable
  public abstract Throwable cause();

  /**
   * Determines whether this log entry and the given log entry were emitted in the same thread
   * (without revealing the specific mechanism by which thread identity is defined).
   */
  public boolean hasSameThreadAs(LogEntry entry) {
    return threadId().equals(entry.threadId());
  }

  /**
   * Returns whether the log entry's metadata has the given key-value pair using comparison methods
   * suitable for tests.
   *
   * <p>Since captured metadata has lost type information, this method does NOT simply check that
   * the given value is in the metadata. Values are compared in 4 distinct classes; integrals,
   * floating point, booleans and strings. So if, for example, the metadata contains a {@code Long}
   * value, but this method is given an {@code Integer} of the same value, then it is considered to
   * be contained in the metadata. However, if the metadata contains {@link Boolean#TRUE} but this
   * method is given the string {@code "true"}, then it is NOT considered to be contained in the
   * metadata.
   *
   * <p>Warning: While this method supports approximate comparisons with floating point values to
   * allow floating point tests to be written, it is almost always a bad idea to do this, and users
   * are strongly advised to find an alternative attribute of the log entry to test.
   *
   * @param key metadata key/label.
   * @param value value to test (if only the existence of a key is being tested, use {@link
   *     #hasMetadataKey(String)}).
   */
  public boolean hasMetadata(String key, Object value) {
    checkNotNull(value, "value must not be null (did you mean 'hasMetadataKey(...)'?)");
    ImmutableList<Object> values = metadata().get(key);
    if (values == null) {
      return false;
    }
    return values.stream().anyMatch(metadataValuePredicate(value));
  }

  /**
   * Returns whether the log entry's metadata has the given key (even if there are no associated
   * values). If you want to test that a key exists with some value, you should use {@link
   * #hasMetadata(String, Object)} and provide a specific value.
   */
  public boolean hasMetadataKey(String key) {
    return metadata().containsKey(key);
  }

  private static Predicate<Object> metadataValuePredicate(Object value) {
    if (value instanceof Number) {
      BigDecimal expected = toBigDecimal((Number) value);
      // NOTE: Cannot use Object::equals for integrals since, due to scale/precision difference:
      // BigDecimal.valueOf(10D) != BigDecimal.valueOf(10L)
      Predicate<BigDecimal> equalityTest =
          (value instanceof Float || value instanceof Double)
              ? n -> closeEnoughToEqualForTesting(expected, n)
              : n -> expected.compareTo(n) == 0;
      return v -> v instanceof Number && equalityTest.test(toBigDecimal((Number) v));
    }
    if (value instanceof Boolean) {
      return v -> v.equals(value);
    }
    String s = value.toString();
    return v -> !(v instanceof Number || v instanceof Boolean) && v.toString().equals(s);
  }

  // Non-floating point values have integral decimal representation and can be compared exactly.
  private static BigDecimal toBigDecimal(Number v) {
    if (v instanceof BigDecimal) {
      return (BigDecimal) v;
    }
    if (v instanceof BigInteger) {
      return new BigDecimal((BigInteger) v);
    }
    return (v instanceof Float || v instanceof Double)
        ? BigDecimal.valueOf(v.doubleValue())
        : BigDecimal.valueOf(v.longValue());
  }

  private static boolean closeEnoughToEqualForTesting(BigDecimal a, BigDecimal b) {
    BigDecimal abs = a.subtract(b).abs();
    // The scale of 10 is arbitrary but not unreasonable for testing purposes. People should not
    // be testing doubles much anyway and adding options to change this encourages bad behaviour.
    // Don't scale up until we know the result will fit into a long (so we test it's less than 1).
    int signum = abs.compareTo(BigDecimal.ONE);
    return signum == 0 || (signum < 0 && abs.scaleByPowerOfTen(10).longValue() == 0);
  }

  /**
   * A deliberately concise representation of the log, intended for showing in test failures without
   * causing too much "spammy" output, while retaining enough information to identify the log
   * statement from which it came. In particular, this string representation may truncate log
   * messages and only show summaries of other values.
   *
   * <p><em>This representation is designed to be human readable only, and can change at any
   * time.</em>
   */
  @Override
  public final String toString() {
    String logSiteString = className();
    logSiteString = logSiteString.substring(logSiteString.lastIndexOf('.') + 1);
    logSiteString += "#" + methodName();
    String causeStr = cause() != null ? ", cause=" + cause().getClass().getSimpleName() : "";
    String metadataStr = !metadata().isEmpty() ? ", context=" + metadata() : "";
    return logSiteString + "@" + snippet() + causeStr + metadataStr;
  }

  /**
   * Treat log entries as unique instances when using in sets or as keys. Nothing in a log instance
   * is guaranteed to be unique, and having two logs with the same message, and even timestamp, is
   * quite possible; and if this happens, these must be considered distinct instances.
   */
  @Override
  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  @Override
  public final int hashCode() {
    return super.hashCode();
  }

  /**
   * A one line snippet aimed at identifying a log entry as part of an error message.
   *
   * <p>It is going to be very common that this is shown for errors in assertions near to the test
   * code which generated this log entry, so it should be unlikely to be ambiguous to the user.
   */
  public String snippet() {
    // For JDK level show just "FINE", for Log4J show "DEBUG(FINE)" etc.
    String levelString = levelName();
    if (!levelString.equals(levelClass().name())) {
      levelString += "(" + levelClass().name() + ")";
    }
    return levelString + ": \"" + shortSnippet(message()) + "\"";
  }

  // Rough and ready trim of the log message to no more than TRIMMED_MESSAGE_LENGTH chars.
  private static String shortSnippet(String message) {
    int splitIndex = findSplit(message);
    if (splitIndex == message.length()) {
      return message;
    }
    if (isHighSurrogate(message.charAt(splitIndex - 1))) {
      splitIndex--;
    }
    // Definitely truncating!
    return message.substring(0, splitIndex) + "...";
  }

  private static int findSplit(String msg) {
    return Math.toIntExact(
        msg.chars().limit(TRIMMED_MESSAGE_LENGTH).takeWhile(c -> c != '\n' && c != '\r').count());
  }

  // Determines class name for testing, ignoring synthetic classes for lambdas etc.
  // This preserves named nested/inner classes and *DOES NOT* strip the class name
  // back to its inferred outer class!!
  private static String inferClassNameForTesting(String name) {
    // https://stackoverflow.com/questions/34589435/get-the-enclosing-class-of-a-java-lambda-expression
    // Assume "ClassName$InnerOrNested$$Lambda$N/xxxxxxx" for JDKs earlier than 11. After this, the
    // lambda name appears as part of the method name, not the class.
    int suffixStart = name.indexOf("$$");
    if (suffixStart == -1) {
      return name;
    }
    // Method name is synthetic (for which there's no published specification), tread carefully.
    if (!name.regionMatches(suffixStart, "$$Lambda$", 0, 9)
        && hasWarnedUnknownClassNamingConvention.compareAndSet(false, true)) {
      Logger.getLogger(LogEntry.class.getName())
          .warning("Unknown synthetic class naming convention: " + name);
    }
    return name.substring(0, suffixStart);
  }

  // Determines method name for testing, ignoring synthetic method names for lambdas etc.
  private static String inferMethodNameForTesting(String name) {
    int nameStart = name.indexOf('$') + 1;
    if (nameStart == 0) {
      return name;
    }
    // Method name is synthetic (for which there's no published specification), tread carefully.
    // Assume "prefix$name[$suffix]" for now which matches "lambda$methodName$N" (JDK 20), but
    // ignore trailing '$N' if not present.
    if (!name.startsWith("lambda$")
        && hasWarnedUnknownMethodNamingConvention.compareAndSet(false, true)) {
      Logger.getLogger(LogEntry.class.getName())
          .warning("Unknown synthetic method naming convention: " + name);
    }
    int nameEnd = name.indexOf('$', nameStart);
    if (nameEnd == -1) {
      nameEnd = name.length();
    }
    return name.substring(nameStart, nameEnd);
  }
}
