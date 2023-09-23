/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.api;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.goui.flogger.testing.api.LogInterceptor.Factory;
import net.goui.flogger.testing.jdk.JdkInterceptor;

final class LogInterceptorLoader {
  private static final Logger logger = Logger.getLogger(LogInterceptorLoader.class.getName());

  public static Supplier<LogInterceptor> loadBestInterceptorFactory() {
    ImmutableList<Factory> factories = ImmutableList.copyOf(ServiceLoader.load(Factory.class));
    if (!FloggerBinding.isFloggerAvailable()) {
      logger.info(
          "Flogger API unavailable, log interceptors cannot be tested.\n"
              + "Depending on the logging API you are using, some features may not work as expected.");
      Factory factory = !factories.isEmpty() ? factories.get(0) : JdkInterceptor.getFactory();
      if (factories.size() > 1) {
        logger.warning(
            "Multiple log interceptors found; using first available service: "
                + factory.getClass().getName());
      } else {
        logger.info("Using log interceptor factory: " + factory.getClass().getName());
      }
      return factory;
    }

    List<Factory> fullSupport = new ArrayList<>();
    List<Factory> partialSupport = new ArrayList<>();
    for (Factory factory : factories) {
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
        logger.warning(
            "Multiple suitable log interceptors found; using factory class: "
                + factory.getClass().getName());
      }
    } else if (!partialSupport.isEmpty()) {
      factory = partialSupport.get(0);
      logger.warning(
          "Log interceptor only has partial support, logging tests may fail spuriously: "
              + factory.getClass().getName());
    } else {
      // No provided interceptors, so try the JDK one.
      factory = JdkInterceptor.getFactory();
      switch (factory.getSupportLevel()) {
        case FULL:
          break;
        case PARTIAL:
          logger.warning(
              "Default log interceptor only has partial support, logging tests may fail spuriously: "
                  + factory.getClass().getName());
          break;
        case NONE:
          logger.warning("No suitable log interceptor detected; logging tests are likely to fail!");
          break;
      }
    }
    return factory;
  }
}
