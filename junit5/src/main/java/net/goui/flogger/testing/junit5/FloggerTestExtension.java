/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

 This program and the accompanying materials are made available under the terms of the
 Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

 SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.junit5;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.logging.Level;
import net.goui.flogger.testing.api.LogInterceptor;
import net.goui.flogger.testing.api.TestingApi;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class FloggerTestExtension extends TestingApi<FloggerTestExtension>
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
    return forClassOrPackage(loggerNameOf(clazz), level);
  }

  public static FloggerTestExtension forPackage(Package pkg, Level level) {
    return forClassOrPackage(pkg.getName(), level);
  }

  public static FloggerTestExtension forClassOrPackage(String loggerName, Level level) {
    return forLevelMap(ImmutableMap.of(loggerName, level), null);
  }

  public static FloggerTestExtension forLevelMap(
      Map<String, ? extends Level> levelMap, Level level) {
    return create(levelMap, null);
  }

  public static FloggerTestExtension create(
      Map<String, ? extends Level> levelMap, @Nullable LogInterceptor interceptor) {
    return new FloggerTestExtension(levelMap, interceptor);
  }

  private FloggerTestExtension(
      Map<String, ? extends Level> levelMap, @Nullable LogInterceptor interceptor) {
    super(levelMap, interceptor);
  }

  @Override
  protected FloggerTestExtension api() {
    return this;
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
