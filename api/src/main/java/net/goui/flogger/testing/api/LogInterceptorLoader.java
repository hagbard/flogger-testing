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
import java.util.logging.Level;
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
              + "Depending on the logging system you are using, some features may not work as expected.\n"
              + "For best results with this library, use a FluentLogger in your application.");
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
          break;
        case PARTIAL:
          partialSupport.add(factory);
          break;
        default:
          break;
      }
    }

    if (fullSupport.size() + partialSupport.size() > 1) {
      logger.info(
          String.format(
              "Multiple log interceptors were available for testing.\n"
                  + "The interceptor with the best support will be used.\n"
                  + "To avoid this message, configure a single logging system during testing.\n"
                  + "\tFully supported interceptors: %s"
                  + "\tPartially supported interceptors: %s",
              fullSupport, partialSupport));
    }

    Factory chosenFactory;
    if (!fullSupport.isEmpty()) {
      chosenFactory = fullSupport.get(0);
    } else if (!partialSupport.isEmpty()) {
      chosenFactory = partialSupport.get(0);
      warnPartialSupport(chosenFactory);
    } else {
      // No provided interceptors, so try the JDK one.
      chosenFactory = JdkInterceptor.getFactory();
      switch (chosenFactory.getSupportLevel()) {
        case FULL:
          break;
        case PARTIAL:
          warnPartialSupport(chosenFactory);
          break;
        case UNKNOWN:
          warnSupport(chosenFactory, "Support for log interceptors could not be determined");
          break;
        default:
          logger.warning("No suitable log interceptor detected; logging tests are likely to fail!");
          break;
      }
    }
    return chosenFactory;
  }

  private static void warnPartialSupport(Factory chosenFactory) {
    warnSupport(chosenFactory, "Only partially supported log interceptors were found");
  }

  private static void warnSupport(Factory chosenFactory, String message) {
    boolean noInfoLogging = logger.isLoggable(Level.INFO);
    String extraInfoMessage =
        noInfoLogging
            ? "\nFor more information, enable FINE logging for: "
                + LogInterceptorLoader.class.getName()
            : "";
    logger.warning(
        String.format(
            "%s. Using: %s%s", message, chosenFactory.getClass().getName(), extraInfoMessage));
    logger.fine(
        "To allow log interceptor support to be inferred reliably, it is important that Flogger\n"
            + "is configured \"close\" to its default behaviour. This includes:\n"
            + "1. Ensuring Flogger is configured to use default message formatting during tests\n"
            + "   (this is especially important for metadata formatted in the 'CONTEXT' section).\n"
            + "2. Ensuring an exact mapping from logging class names to logging backend names.\n"
            + "For more information, see https://github.com/hagbard/flogger-testing");
  }
}
