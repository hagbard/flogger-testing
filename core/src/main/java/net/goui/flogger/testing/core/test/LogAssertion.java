package net.goui.flogger.testing.core.test;

import java.util.logging.Level;

public interface LogAssertion {
  void messageContains(String substring);
}
