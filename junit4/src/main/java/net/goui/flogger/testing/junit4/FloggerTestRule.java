/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

 This program and the accompanying materials are made available under the terms of the
 Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

 SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.junit4;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.SetLogLevel;
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

  // Matches an expected text class name and captures the assumed class-under-test.
  private static final Pattern EXPECTED_TEST_CLASS_NAME =
      Pattern.compile("((?:[^.]+\\.)*[^.]+)Test");

  static String guessClassUnderTest(Class<?> caller) {
    String testClassName = caller.getName();
    Matcher matcher = EXPECTED_TEST_CLASS_NAME.matcher(testClassName);
    checkArgument(
        matcher.matches(),
        "Cannot infer class-under-test (test classes must be named 'XxxTest'): %s",
        testClassName);
    return matcher.group(1);
  }

  static String guessPackageUnderTest(Class<?> caller) {
    String packageName = caller.getPackage().getName();
    checkArgument(
        !packageName.isEmpty(),
        "Cannot infer package-under-test (test classes must not be in the root package): %s",
        caller.getName());
    return packageName;
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

  // Approximate matcher to package names in Java:
  // Avoids bare class names, allows nested and inner classes (with '$').
  private static final Pattern PACKAGE_OR_CLASS_NAME =
      Pattern.compile("(?:[A-Z0-9_$]+\\.)+[A-Z0-9_$]+", CASE_INSENSITIVE);

  private static ImmutableMap<String, LevelClass> getLevelMap(ImmutableList<SetLogLevel> levels) {
    if (levels.isEmpty()) {
      return ImmutableMap.of();
    }
    ImmutableMap.Builder<String, LevelClass> builder = ImmutableMap.builder();
    for (SetLogLevel e : levels) {
      String targetName;
      if (e.target() != Object.class) {
        checkArgument(e.name().isEmpty(), "specify only one of 'target' or 'name': %s", e);
        targetName = e.target().getName();
      } else {
        checkArgument(!e.name().isEmpty(), "specify either 'target' or 'name': %s", e);
        targetName = e.name();
      }
      checkArgument(
          PACKAGE_OR_CLASS_NAME.matcher(targetName).matches(),
          "invalid target class or name (expected xxx.yyy.Zzz): %s",
          targetName);
      builder.put(targetName.replace('$', '.'), e.level());
    }
    return builder.buildOrThrow();
  }
}
