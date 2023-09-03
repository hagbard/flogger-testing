package net.goui.flogger.testing.junit4;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.logging.Level;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.TestApi;
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
 * If this is not the case, specify logger names directly via {@link #forClassOrPackage(String,
 * Level)}.
 */
public final class FloggerTestRule extends TestApi<FloggerTestRule> implements TestRule {

  public static FloggerTestRule forClassUnderTest(Level level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forClass(caller, level);
  }

  public static FloggerTestRule forPackageUnderTest(Level level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forPackage(caller.getPackage(), level);
  }

  public static FloggerTestRule forClass(Class<?> clazz, Level level) {
    return forClassOrPackage(loggerNameOf(clazz), level);
  }

  public static FloggerTestRule forPackage(Package pkg, Level level) {
    return forClassOrPackage(pkg.getName(), level);
  }

  public static FloggerTestRule forClassOrPackage(String loggerName, Level level) {
    return forLevelMap(ImmutableMap.of(loggerName, level), null);
  }

  public static FloggerTestRule forLevelMap(Map<String, ? extends Level> levelMap, Level level) {
    return create(levelMap, null);
  }

  public static FloggerTestRule create(
      Map<String, ? extends Level> levelMap, @Nullable LogInterceptor interceptor) {
    return new FloggerTestRule(levelMap, interceptor);
  }

  private FloggerTestRule(
      Map<String, ? extends Level> levelMap, @Nullable LogInterceptor interceptor) {
    super(levelMap, interceptor);
  }

  @Override
  protected FloggerTestRule api() {
    return this;
  }

  @Override
  public Statement apply(Statement statement, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try (var ctx = install(true)) {
          statement.evaluate();
        }
      }
    };
  }
}
