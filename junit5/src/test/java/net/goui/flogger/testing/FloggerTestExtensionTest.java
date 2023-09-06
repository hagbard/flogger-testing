/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Copyright (c) 2023, David Beaumont (https://github.com/hagbard).

This program and the accompanying materials are made available under the terms of the
Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.

SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package net.goui.flogger.testing;

import com.google.common.flogger.FluentLogger;
import java.util.logging.Level;
import net.goui.flogger.testing.junit5.FloggerTestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

public class FloggerTestExtensionTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @RegisterExtension
  static final FloggerTestExtension logged =
      FloggerTestExtension.forClass(FloggerTestExtensionTest.class, Level.INFO)
          .verify(logs -> logs.always().haveMessageContaining("Hello"));

  @ExtendWith(FloggerTestExtension.class)
  @Test
  public void testFoo() {
    logger.atInfo().log("Hello World!");

    logged.assertLog(0).hasMessageContaining("Hello");
  }
}
