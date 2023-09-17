/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.junit4;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.SetLogLevel;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.TestingApi;
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
 * FloggerTestRule logs = FloggerTestRule.forClassUnderTest(INFO);
 * }</pre>
 *
 * <p>The test rule listed above will ensure that {@code INFO} logs (and above) are enabled, and
 * bypass rate limiting, for the class being tested (assuming standard Java test class naming).
 *
 * <p>Some methods in this class assume that a logger's name is the name of the class which uses it.
 * If this is not the case, specify logger names directly via {@link #forClassOrPackage(String,
 * LevelClass)}.
 */
public final class FloggerTestRule extends TestingApi<FloggerTestRule> implements TestRule {
  public static FloggerTestRule forClassUnderTest(LevelClass level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forClassOrPackage(guessClassUnderTest(caller), level);
  }

  public static FloggerTestRule forPackageUnderTest(LevelClass level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forClassOrPackage(guessPackageUnderTest(caller), level);
  }

  public static FloggerTestRule forClass(Class<?> clazz, LevelClass level) {
    return forClassOrPackage(loggerNameOf(clazz), level);
  }

  public static FloggerTestRule forPackage(Package pkg, LevelClass level) {
    return forClassOrPackage(pkg.getName(), level);
  }

  public static FloggerTestRule forClassOrPackage(String loggerName, LevelClass level) {
    return forLevelMap(ImmutableMap.of(loggerName, level));
  }

  public static FloggerTestRule forLevelMap(Map<String, LevelClass> levelMap) {
    return create(levelMap, null);
  }

  public static FloggerTestRule create(
      Map<String, LevelClass> levelMap, @Nullable LogInterceptor interceptor) {
    return new FloggerTestRule(levelMap, interceptor);
  }

  private FloggerTestRule(Map<String, LevelClass> levelMap, @Nullable LogInterceptor interceptor) {
    super(levelMap, interceptor);
  }

  @Override
  protected FloggerTestRule api() {
    return this;
  }

  @Override
  public Statement apply(Statement statement, Description description) {
    ImmutableMap<String, LevelClass> extraLogLevels =
        getLevelMap(
            description.getTestClass(),
            description.getAnnotations().stream()
                .filter(SetLogLevel.class::isInstance)
                .map(SetLogLevel.class::cast)
                .collect(toImmutableList()));
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try (var ctx = install(true, extraLogLevels)) {
          statement.evaluate();
        }
      }
    };
  }
}
