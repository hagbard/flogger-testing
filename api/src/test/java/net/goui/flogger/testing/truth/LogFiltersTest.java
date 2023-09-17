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

import static com.google.common.truth.Truth.assertThat;
import static net.goui.flogger.testing.truth.LogFilters.containsAllFragmentsInOrder;
import static net.goui.flogger.testing.truth.LogFilters.joinFragments;
import static org.junit.Assert.assertThrows;

import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogFiltersTest extends TestCase {
  @Test
  public void testContainsAllFragmentsInOrder() {
    assertThat(containsAllFragmentsInOrder("hello world", "hello")).isTrue();
    assertThat(containsAllFragmentsInOrder("hello world", "hello", "world")).isTrue();

    assertThat(containsAllFragmentsInOrder("hello world", "world", "hello")).isFalse();
    assertThat(containsAllFragmentsInOrder("hello world", "hello", "hello")).isFalse();

    assertThat(containsAllFragmentsInOrder("inform about formation", "inform", "formation"))
        .isTrue();
    assertThat(containsAllFragmentsInOrder("information", "inform", "formation")).isFalse();
  }

  @Test
  public void testContainsAllFragmentsInOrder_errors() {
    assertThrows(
        IllegalArgumentException.class, () -> containsAllFragmentsInOrder("hello world", ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> containsAllFragmentsInOrder("hello world", "hello", ""));
    assertThrows(
        NullPointerException.class, () -> containsAllFragmentsInOrder("hello world", null));
    assertThrows(
        NullPointerException.class,
        () -> containsAllFragmentsInOrder("hello world", "hello", (String) null));
  }

  @Test
  public void testJoinFragments() {
    assertThat(joinFragments("foo")).isEqualTo("'foo'");
    assertThat(joinFragments("foo", "bar")).isEqualTo("'foo', 'bar'");
    assertThat(joinFragments("foo", "bar", "baz")).isEqualTo("'foo', 'bar', 'baz'");

    // Shouldn't happen but at least test what it does in case it needs to be changeds.
    assertThat(joinFragments(null)).isEqualTo("'null'");
    assertThat(joinFragments("foo", (String) null)).isEqualTo("'foo', 'null'");
  }
}
