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


import net.goui.flogger.testing.api.LogInterceptor.Support;

/**
 * An implementation of {@link LogInterceptor.Factory} which supplies its own test to the determine
 * support level of an interceptor. Subclasses need only configure an underlying logger instance
 * programmatically in order to get a good measure of support.
 */
public abstract class AbstractLogInterceptorFactory implements LogInterceptor.Factory {
  protected abstract void configureUnderlyingLoggerForInfoLogging(String loggerName);

  /**
   * A fairly comprehensive test to determine the support level for an interceptor using {@code
   * FluentLogger} to sample the behaviour of the subclass interceptor implementation.
   *
   * @return the support level of the implementation subclass.
   */
  @Override
  public final Support getSupportLevel() {
    return FloggerBinding.getSupportLevel(this);
  }
}
