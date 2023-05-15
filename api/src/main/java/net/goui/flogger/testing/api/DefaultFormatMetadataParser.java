package net.goui.flogger.testing.api;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Longs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DefaultFormatMetadataParser {
  // Default Flogger log message formatting adds metadata consistently in the form:
  // [CONTEXT a=b x="y" ]
  // Non-quoted values are from tag values boolean, long or double and can be re-parsed.
  // Quoted values are JSON escaped (\\, \", \n, \r, \t) and need unescaping.
  private static final Pattern KEY_VALUE_PAIR =
      Pattern.compile("([^\\s=]+)(?:=([^\"]\\S*|\"(?:[^\"\\\\]|\\\\[\\\\nrt\"])+\"))?");
  private static final Pattern CONTEXT =
      Pattern.compile("(?:^|[ \\n])\\[CONTEXT ((?:" + KEY_VALUE_PAIR.pattern() + " )+)]$");

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
    if (valueString.charAt(0) != '"') {
      if (valueString.equalsIgnoreCase("true")) {
        return true;
      }
      if (valueString.equalsIgnoreCase("false")) {
        return false;
      }
      Object value = Longs.tryParse(valueString);
      if (value == null) {
        value = Doubles.tryParse(valueString);
        if (value == null) {
          value = valueString;
        }
      }
      return value;
    }
    String jsonString = valueString.substring(1, valueString.length() - 1);
    int end = jsonString.indexOf('\\');
    // Bail if the string doesn't need unescaping or if ends in a bare `\` (it was corrupt somehow).
    if (end == -1 || jsonString.charAt(jsonString.length() - 1) == '\\') {
      return jsonString;
    }
    StringBuilder buf = new StringBuilder(jsonString.length());
    int start = 0;
    do {
      buf.append(jsonString, start, end);
      char c = jsonString.charAt(end + 1);
      int i = "nrt".indexOf(c);
      buf.append((i != -1) ? "\n\r\t".charAt(i) : c);
      start = end + 2;
      end = jsonString.indexOf('\\', start);
    } while (end != -1);
    buf.append(jsonString, start, jsonString.length());
    return buf.toString();
  }
}
