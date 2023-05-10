package net.goui.flogger.testing.core.truth;

import java.util.logging.Level;

public interface LogAssertion {
  void messageContains(String substring);

  void messageMatches(String regex);

  void metadataContains(String key, boolean value);

  void metadataContains(String key, long value);

  void metadataContains(String key, double value);

  void metadataContains(String key, String value);

  void hasCause(Class<? extends Throwable> type);

  void levelIsCompatibleWith(Level level);

  void levelIsAbove(Level level);

  void levelIsBelow(Level level);
}
