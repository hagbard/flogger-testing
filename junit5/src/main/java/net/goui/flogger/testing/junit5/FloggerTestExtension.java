/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

 This program and the accompanying materials are made available under the terms of the
 Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

 SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.junit5;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
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
  public static FloggerTestExtension forClassUnderTest(LevelClass level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forClassOrPackage(guessClassUnderTest(caller), level);
  }

  public static FloggerTestExtension forPackageUnderTest(LevelClass level) {
    Class<?> caller = StackWalker.getInstance(RETAIN_CLASS_REFERENCE).getCallerClass();
    return forClassOrPackage(guessPackageUnderTest(caller), level);
  }

  public static FloggerTestExtension forClass(Class<?> clazz, LevelClass level) {
    return forClassOrPackage(loggerNameOf(clazz), level);
  }

  public static FloggerTestExtension forPackage(Package pkg, LevelClass level) {
    return forClassOrPackage(pkg.getName(), level);
  }

  public static FloggerTestExtension forClassOrPackage(String loggerName, LevelClass level) {
    return forLevelMap(ImmutableMap.of(loggerName, level));
  }

  public static FloggerTestExtension forLevelMap(Map<String, LevelClass> levelMap) {
    return create(levelMap, null);
  }

  public static FloggerTestExtension create(
      Map<String, LevelClass> levelMap, @Nullable LogInterceptor interceptor) {
    return new FloggerTestExtension(levelMap, interceptor);
  }

  // Matches an expected text class name and captures the assumed class-under-test.
  private static final Pattern EXPECTED_TEST_CLASS_NAME =
      Pattern.compile("((?:[^.]+\\.)*[^.]+)Test");

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
