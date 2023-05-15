package net.goui.flogger.testing.junit4;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.goui.flogger.testing.core.LogInterceptor;
import net.goui.flogger.testing.core.TestApi;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A JUnit rule to "force" a subset of log statements during tests. This allows reliable testing of
 * rate-limited or "fine" log statements without needing to enable all logging.
 *
 * <p>To use this rule, simply install a {@code @Rule} in your test:
 *
 * <pre>{@code
 * @Rule
 * FloggerTestRule logs = FloggerTestRule.forClaóssUnderTest(INFO);
 * }</pre>
 *
 * <p>The test rule listed above will ensure that {@code INFO} logs (and above) are enabled, and
 * bypass rate limiting, for the class being tested (assuming standard Java test class naming).
 *
 * <p>Some methods in this class assume that a logger's name is the name of the class which uses it.
 * If this is not the case, specify logger names directly via {@link #of(String, Level)} or {@link
 * #using(Map)}.
 */
public final class FloggerTestRule extends TestApi implements TestRule {
  public static FloggerTestRule forClassUnderTest(Level level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forClass(caller, level);
  }

  public static FloggerTestRule forPackageUnderTest(Level level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forPackage(caller.getPackage(), level);
  }

  public static FloggerTestRule forClass(Class<?> clazz, Level level) {
    return of(loggerNameOf(clazz), level);
  }

  public static FloggerTestRule forPackage(Package pkg, Level level) {
    return of(pkg.getName(), level);
  }

  /**
   * @param loggerName a dot-separated hierarchical logger name.
   * @param level
   * @return
   */
  public static FloggerTestRule of(String loggerName, Level level) {
    return using(ImmutableMap.of(loggerName, level));
  }

  /**
   * @param levelMap a map of dot-separated hierarchical logger names to corresponding levels.
   * @return
   */
  public static FloggerTestRule using(Map<String, ? extends Level> levelMap) {
    return new FloggerTestRule(levelMap, null, null);
  }

  private FloggerTestRule(
      Map<String, ? extends Level> levelMap,
      @Nullable LogInterceptor interceptor,
      @Nullable Consumer<TestApi> commonAssertions) {
    super(levelMap, interceptor, commonAssertions);
  }

  FloggerTestRule withInterceptor(LogInterceptor interceptor) {
    return new FloggerTestRule(levelMap(), interceptor, commonAssertions());
  }

  FloggerTestRule asserting(Consumer<TestApi> assertions) {
    Consumer<TestApi> newAssertions =
        commonAssertions() == null
            ? assertions
            : t -> {
              commonAssertions().accept(t);
              assertions.accept(t);
            };
    return new FloggerTestRule(levelMap(), interceptor(), newAssertions);
  }

  @Override
  public Statement apply(Statement statement, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try (var ctx = install()) {
          statement.evaluate();
        }
      }
    };
  }
}
