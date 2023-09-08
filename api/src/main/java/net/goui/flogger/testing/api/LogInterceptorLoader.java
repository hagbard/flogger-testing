/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

This program and the accompanying materials are made available under the terms of the
Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing.api;

import com.google.common.flogger.FluentLogger;
import java.util.*;
import java.util.function.Supplier;
import net.goui.flogger.testing.api.LogInterceptor.Factory;
import net.goui.flogger.testing.jdk.JdkInterceptor;

final class LogInterceptorLoader {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ServiceLoader<Factory> loader = ServiceLoader.load(Factory.class);

  public static Supplier<LogInterceptor> loadBestInterceptorFactory() {
    List<Factory> fullSupport = new ArrayList<>();
    List<Factory> partialSupport = new ArrayList<>();
    for (Factory factory : loader) {
      switch (factory.getSupportLevel()) {
        case FULL:
          fullSupport.add(factory);
        case PARTIAL:
          partialSupport.add(factory);
        case NONE:
          break;
      }
    }
    Factory factory;
    if (!fullSupport.isEmpty()) {
      factory = fullSupport.get(0);
      if (fullSupport.size() > 1) {
        logger.atWarning().log(
            "Multiple log interceptors found; using factory '%s'", factory.getClass().getName());
      }
    } else if (!partialSupport.isEmpty()) {
      factory = partialSupport.get(0);
      logger.atSevere().log(
          "Detected log interceptor factory '%s' only has partial capture support.\n"
              + "Logging tests may fail spuriously!",
          factory.getClass().getName());
    } else {
      factory = new JdkInterceptor.Factory();
      switch (factory.getSupportLevel()) {
        case FULL:
          break;
        case PARTIAL:
          logger.atSevere().log(
              "Detected log interceptor factory '%s' only has partial capture support.\n"
                  + "Logging tests may fail spuriously!",
              JdkInterceptor.class.getName());
          break;
        case NONE:
          logger.atSevere().log(
              "No suitable log interceptor detected; logging tests are likely to fail!");
          break;
      }
    }
    return factory;
  }
}
