package net.goui.flogger.testing.truth;

import net.goui.flogger.testing.LevelClass;

/**
 * The set of assertions which can be carried out on a single log entry, or a set of entries. These
 * methods form the bulk of the terminal methods in the fluent API chain.
 *
 * <h2>Design Notes</h2>
 *
 * <p>The API presented here is deliberately minimized to avoid encouraging poor test practice. Logs
 * testing is extremely prone to be "brittle" when overly precise assertions are made, and this API
 * attempts to reduce the change of "brittleness", while still satisfying all common use-cases.
 *
 * <p>These methods are named for the "singular" case, but may be applied to all entries in a
 * sequence (e.g. {@code assertLogs().everyLog().hasMetadata("id", 1234)}). Other methods in the
 * fluent API are carefully named to make these methods read as naturally as possible.
 *
 * <p>To assert the negative statement, the {@code noLog()} selector can be used in the fluent chain
 * (e.g. {@code assertLogs().noLog().hasMetadata("id", 1234)}). This avoids the need to have methods
 * such as {@code doesNotContain()} in this API.
 */
public interface LogAssertion {
  /**
   * Asserts that the log entry/entries under test have a message which contains a specific
   * substring. Tests should assert only important information in a log message and avoid testing
   * for content which is prone to change through normal refactoring.
   */
  void contains(String substring);

  /**
   * Asserts that the log entry/entries under test have a message which contains a substring which
   * matches a given regular expression. Tests should assert only important information in a log
   * message and avoid testing for content which is prone to change through normal refactoring.
   */
  void containsMatch(String regex);

  /**
   * Asserts that the log entry/entries under test have metadata with the given key/value pair.
   * Metadata has no inherent order, and tests should only look for the metadata they care about.
   */
  void hasMetadata(String key, boolean value);

  /**
   * Asserts that the log entry/entries under test have metadata with the given key/value pair.
   * Metadata has no inherent order, and tests should only look for the metadata they care about.
   */
  void hasMetadata(String key, long value);

  /**
   * Asserts that the log entry/entries under test have metadata with the given key/value pair.
   * Metadata has no inherent order, and tests should only look for the metadata they care about.
   */
  void hasMetadata(String key, double value);

  /**
   * Asserts that the log entry/entries under test have metadata with the given key/value pair.
   * Metadata has no inherent order, and tests should only look for the metadata they care about.
   */
  void hasMetadata(String key, String value);

  /**
   * Asserts that the log entry/entries under test have an associated "cause" of the specified type.
   * Often it is sufficient to just test that a log contains a "cause", rather than asserting
   * something specific about it as these are often values created in code outside the control of
   * the code under test.
   */
  void hasCause(Class<? extends Throwable> type);

  /** Asserts that the log entry/entries under test were logged at the given level class. */
  void isAtLevel(LevelClass level);
}
