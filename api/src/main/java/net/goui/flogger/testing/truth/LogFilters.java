/*******************************************************************************
 * Copyright (c) 2023, David Beaumont (https://github.com/hagbard).
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v. 2.0 available at https://www.eclipse.org/legal/epl-2.0, or the
 * Apache License, Version 2.0 available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ******************************************************************************/

package net.goui.flogger.testing.truth;

import static com.google.common.base.Preconditions.checkArgument;

/** Internal shared static log entry filtering methods. */
final class LogFilters {
  /** Support method for things like {@link LogSubject#hasMessageContaining(String, String...)}. */
  static boolean containsAllFragmentsInOrder(String message, String firstFragment, String... rest) {
    int offset = offsetOfFragment(message, firstFragment, 0);
    // Don't exit early in order to always check that all fragments are non-empty.
    for (String fragment : rest) {
      offset = offsetOfFragment(message, fragment, offset);
    }
    return offset >= 0;
  }

  private static int offsetOfFragment(String message, String fragment, int offset) {
    checkArgument(!fragment.isEmpty(), "message fragments must not be empty");
    if (offset >= -1) {
      int start = message.indexOf(fragment, offset);
      offset = start >= 0 ? start + fragment.length() : -1;
    }
    return offset;
  }

  /** Support method for things like {@link LogSubject#hasMessageContaining(String, String...)}. */
  static String joinFragments(String fragment, String... rest) {
    if (rest.length == 0) {
      return "'" + fragment + "'";
    }
    return "'" + fragment + "', '" + String.join("', '", rest) + "'";
  }
}
