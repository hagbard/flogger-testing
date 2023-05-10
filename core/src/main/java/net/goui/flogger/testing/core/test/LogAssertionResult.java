package net.goui.flogger.testing.core.test;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;

@AutoValue
abstract class LogAssertionResult {
  static final LogAssertionResult PASS = new AutoValue_LogAssertionResult(true, ImmutableList.of());

  abstract boolean passed();

  abstract ImmutableList<Fact> facts();

  static LogAssertionResult fail(Fact... facts) {
    checkArgument(facts.length > 0);
    return new AutoValue_LogAssertionResult(false, ImmutableList.copyOf(facts));
  }

  final Fact[] describeFailure() {
    return facts().toArray(Fact[]::new);
  }
}
