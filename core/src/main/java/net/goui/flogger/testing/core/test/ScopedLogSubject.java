package net.goui.flogger.testing.core.test;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.util.logging.Level;

public class ScopedLogSubject extends Subject implements LogAssertion {
  private final ScopedLog log;

  protected ScopedLogSubject(FailureMetadata metadata, ScopedLog log) {
    super(metadata, log);
    this.log = log;
  }

  public static Factory<ScopedLogSubject, ScopedLog> scopedLogs() {
    return ScopedLogSubject::new;
  }

  public static ScopedLogSubject assertThat(ScopedLog log) {
    return assertAbout(scopedLogs()).that(log);
  }

//  LogAssertion at(Level level) {
//
//  }

  @Override
  public void messageContains(String substring) {
    LogAssertionResult result = log.assertLogs(e -> e.message().contains(substring));
    if (!result.passed()) {
      failWithoutActual(
          Fact.fact("expected " + log.strategyDescription() + " log message to contain substring", substring),
          result.describeFailure()
          );
    }
  }
}
