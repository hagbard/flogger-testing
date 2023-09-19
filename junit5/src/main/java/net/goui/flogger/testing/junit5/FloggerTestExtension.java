/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.junit5;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.SetLogLevel;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.TestingApi;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class FloggerTestExtension extends TestingApi<FloggerTestExtension>
    implements BeforeEachCallback, AfterEachCallback {
  /**
   * Returns a test fixture for the class-under-test at the specified level. It is assumed that the
   * class being tested and the test class are named {@code Foo} and {@code FooTest} respectively,
   * and exist in the same package.
   */
  public static FloggerTestExtension forClassUnderTest(LevelClass level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forClassOrPackage(guessClassUnderTest(caller), level);
  }

  /**
   * Returns a test fixture for all classes in the package-under-test at the specified level. It is
   * assumed that the classes being tested and the test class exist in the same package. This will
   * also capture logs from "sub packages" (i.e. those sharing the same package prefix).
   */
  public static FloggerTestExtension forPackageUnderTest(LevelClass level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forClassOrPackage(guessPackageUnderTest(caller), level);
  }

  /** Returns a test fixture for the given class at the specified level. */
  public static FloggerTestExtension forClass(Class<?> clazz, LevelClass level) {
    return forClassOrPackage(loggerNameOf(clazz), level);
  }

  /**
   * Returns a test fixture for the given package at the specified level. This will also capture
   * logs from "sub packages" (i.e. those sharing the same package prefix).
   */
  public static FloggerTestExtension forPackage(Package pkg, LevelClass level) {
    return forClassOrPackage(pkg.getName(), level);
  }

  /**
   * Returns a test fixture for the named class or package at the specified level. Note that this is
   * often a more brittle way to specify what's being captured and can easily break under
   * refactoring.
   */
  public static FloggerTestExtension forClassOrPackage(String loggerName, LevelClass level) {
    return forLevelMap(ImmutableMap.of(loggerName, level));
  }

  /**
   * Returns a test fixture which captures log for all entries in the given map of class/package
   * names to log level. This is the most general case you normally need to use, and can specify
   * different log levels for different classes/packages. However, if you find yourself using this
   * method regularly, it might be a sign that your logging testing is not well encapsulated.
   *
   * @param levelMap a map of class/package names to the corresponding log level which is to be
   *     captured for testing.
   */
  public static FloggerTestExtension forLevelMap(Map<String, LevelClass> levelMap) {
    return create(levelMap, null);
  }

  /**
   * A special-case method to create a log fixture with a specified log interceptor. In general, you
   * should not need to use this method.
   */
  public static FloggerTestExtension create(
      Map<String, LevelClass> levelMap, @Nullable LogInterceptor interceptor) {
    return new FloggerTestExtension(levelMap, interceptor);
  }

  private FloggerTestExtension(
      Map<String, LevelClass> levelMap, @Nullable LogInterceptor interceptor) {
    super(levelMap, interceptor);
  }

  @Override
  protected FloggerTestExtension api() {
    return this;
  }

  private final AtomicReference<ApiHook> apiHook = new AtomicReference<>();

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    ImmutableMap<String, LevelClass> extraLogLevels =
        getLevelMap(
            extensionContext.getTestClass().orElseThrow(),
            Arrays.stream(extensionContext.getTestMethod().orElseThrow().getAnnotations())
                .filter(SetLogLevel.class::isInstance)
                .map(SetLogLevel.class::cast)
                .collect(toImmutableList()));
    checkState(
        apiHook.getAndSet(install(true, extraLogLevels)) == null,
        "API hook must never be installed twice");
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) {
    // Permit "afterEach()" to be called more than once, but only call hook.close() once.
    ApiHook hook = apiHook.getAndSet(null);
    if (hook != null) {
      hook.close();
    }
  }
}
