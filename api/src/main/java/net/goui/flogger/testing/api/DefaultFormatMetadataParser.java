/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

 This program and the accompanying materials are made available under the terms of the
 Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

 SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.api;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Longs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class to re-parse metadata from the default format used by Flogger. This permits
 * logging tests to make approximately accurate assertions about the metadata of a log statement
 * even when that metadata was formatting into the log message.
 *
 * <p>The process is approximate because only 4 data types can be inferred from the textual
 * representation ({@code Boolean}, {@code Long}, {@code Double} and {@code String}). So metadata
 * originally specified as an {@code Integer} will be parsed as a {@code Long} etc.
 *
 * <p>Strings, which are JSON escaped in the formatted output, are unescaped during parsing, so
 * exact comparisons can be reliably made.
 *
 * <p>However, if a metadata key/value has been moved elsewhere in the message (e.g. by using a
 * custom format specifier) then it will not be extracted. To get around this, it is strongly
 * suggested that tests be run without custom Flogger formatting enabled.
 */
public final class DefaultFormatMetadataParser {
  // Default Flogger log message formatting adds metadata consistently in the form:
  // [CONTEXT a=b x="y" ]
  // Non-quoted values are from tag values boolean, long or double and can be reparsed.
  // Quoted values are JSON escaped (\\, \", \n, \r, \t) and need unescaping.
  private static final Pattern KEY_VALUE_PAIR =
      Pattern.compile("([^\\s=]+)(?:=([^\"]\\S*|\"(?:[^\"\\\\]|\\\\[\\\\nrt\"])+\"))?");
  private static final Pattern CONTEXT =
      Pattern.compile("(?:^|[ \\n])\\[CONTEXT ((?:" + KEY_VALUE_PAIR.pattern() + " )+)]$");

  /**
   * Parses a formatted log message, potentially containing a metadata section, to extract and
   * separate the metadata from the remaining log message.
   */
  public static MessageAndMetadata parse(String message) {
    Matcher m = CONTEXT.matcher(message);
    Map<String, List<Object>> metadata = ImmutableMap.of();
    if (m.find()) {
      message = message.substring(0, m.start());
      String keyValuePairs = m.group(1);

      metadata = new HashMap<>();
      Matcher pairs = KEY_VALUE_PAIR.matcher(keyValuePairs);
      while (pairs.find()) {
        String key = pairs.group(1);
        String value = pairs.group(2);
        List<Object> values = metadata.computeIfAbsent(key, k -> new ArrayList<>());
        if (value != null) {
          values.add(parseValue(value));
        }
      }
    }
    return MessageAndMetadata.of(message, metadata);
  }

  private static Object parseValue(String valueString) {
    boolean foundProblems = false;
    // Deal with non-string types (Boolean, Long, Double) first in order of parser ambiguity.
    if (valueString.charAt(0) != '"') {
      if (valueString.equalsIgnoreCase("true")) {
        return true;
      }
      if (valueString.equalsIgnoreCase("false")) {
        return false;
      }
      // Parse for a long first to avoid long values being parsed to doubles.
      Object value = Longs.tryParse(valueString);
      if (value == null) {
        value = Doubles.tryParse(valueString);
        if (value == null) {
          // Give up and just use the value as a string (not ideal, but it retains useful debug
          // information at least).
          foundProblems = true;
          Lazy.logger
              .atFine()
              .log(
                  "Failed to parse metadata value '%s' from log message\n"
                      + "It was expected to be one of boolean, long or double",
                  value);
          value = valueString;
        }
      }
      return value;
    }
    String jsonString = valueString.substring(1, valueString.length() - 1);
    int end = jsonString.indexOf('\\');
    int lastIndex = jsonString.length() - 1;
    if (end == -1) {
      // No unescaping needed.
      return jsonString;
    }
    // JSON unescaping (should be no longer than the original string).
    StringBuilder buf = new StringBuilder(jsonString.length());
    int start = 0;
    do {
      if (end == lastIndex) {
        foundProblems = true;
        Lazy.logger
            .atFine()
            .log("Unexpected trailing backslash found in value string: %s", valueString);
        // This still adds the final chunk (with the trailing backslash in).
        break;
      }
      buf.append(jsonString, start, end);

      char c = jsonString.charAt(end + 1);
      int i = "\\\"nrt".indexOf(c);
      if (i != -1) {
        buf.append("\\\"\n\r\t".charAt(i));
      } else {
        foundProblems = true;
        Lazy.logger
            .atFine()
            .log(
                "Unexpected escaped character '\\%c' in metadata value string: %s", c, valueString);
        buf.append(c);
      }
      start = end + 2;
      end = jsonString.indexOf('\\', start);
    } while (end != -1);
    String unescaped = buf.append(jsonString, start, jsonString.length()).toString();
    if (foundProblems) {
      Lazy.logger
          .atWarning()
          .log(
              "Problems found while parsing metadata value string: '%s'\n"
                  + "Unescaped value '%s' may not be accurate and could affect test results."
                  + "To debug further, enable FINE logging for class: %s",
              valueString, unescaped, MetadataExtractor.class.getName());
    }
    return unescaped;
  }

  private static final class Lazy {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  }
}
