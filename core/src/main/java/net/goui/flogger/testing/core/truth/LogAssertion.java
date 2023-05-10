package net.goui.flogger.testing.core.truth;

import java.util.logging.Level;

public interface LogAssertion {
  void messageContains(String substring);
  void messageMatches(String regex);

  void levelIsCompatibleWith(Level level);
  void levelIsAbove(Level level);
  void levelIsBelow(Level level);
}