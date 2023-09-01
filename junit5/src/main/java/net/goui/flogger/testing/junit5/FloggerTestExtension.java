package net.goui.flogger.testing.junit5;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.TestApi;
import net.goui.flogger.testing.truth.LogsSubject;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class FloggerTestExtension extends TestApi
    implements BeforeEachCallback, AfterEachCallback {
  public static FloggerTestExtension forClassUnderTest(Level level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forClass(caller, level);
  }

  public static FloggerTestExtension forPackageUnderTest(Level level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forPackage(caller.getPackage(), level);
  }

  public static FloggerTestExtension forClass(Class<?> clazz, Level level) {
    return of(loggerNameOf(clazz), level);
  }

  public static FloggerTestExtension forPackage(Package pkg, Level level) {
    return of(pkg.getName(), level);
  }

  /**
   * @param loggerName a dot-separated hierarchical logger name.
   * @param level
   * @return
   */
  public static FloggerTestExtension of(String loggerName, Level level) {
    return using(ImmutableMap.of(loggerName, level));
  }

  /**
   * @param levelMap a map of dot-separated hierarchical logger names to corresponding levels.
   * @return
   */
  public static FloggerTestExtension using(Map<String, ? extends Level> levelMap) {
    return new FloggerTestExtension(levelMap, null, null);
  }

  private FloggerTestExtension(
      Map<String, ? extends Level> levelMap,
      @Nullable LogInterceptor interceptor,
      @Nullable Consumer<LogsSubject> commonAssertions) {
    super(levelMap, interceptor, commonAssertions);
  }

  FloggerTestExtension withInterceptor(LogInterceptor interceptor) {
    checkArgument(interceptor() == null, "interceptor was already set: %s", interceptor());
    return new FloggerTestExtension(levelMap(), interceptor, commonAssertions());
  }

  public FloggerTestExtension verify(Consumer<LogsSubject> assertions) {
    Consumer<LogsSubject> newAssertions =
        commonAssertions() == null
            ? assertions
            : t -> {
              commonAssertions().accept(t);
              assertions.accept(t);
            };
    return new FloggerTestExtension(levelMap(), interceptor(), newAssertions);
  }
  
  private ApiHook apiHook = null;
  
  @Override
  public void beforeEach(ExtensionContext extensionContext) throws Exception {
    apiHook = install(true);
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) throws Exception {
    if (apiHook != null) {
      apiHook.close();
    }
  }
}
