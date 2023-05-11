package net.goui.flogger.testing.core.truth;

import net.goui.flogger.testing.core.LogEntry.LevelClass;

public interface LogAssertion {
  void messageContains(String substring);

  void messageMatches(String regex);

  void metadataContains(String key, boolean value);

  void metadataContains(String key, long value);

  void metadataContains(String key, double value);

  void metadataContains(String key, String value);

  void hasCause(Class<? extends Throwable> type);

  void levelIs(LevelClass level);

  void levelIsAbove(LevelClass level);

  void levelIsBelow(LevelClass level);
}
