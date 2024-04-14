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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultFormatMetadataParserTest {
  @Test
  public void parse_extractsMessage_success() {
    assertMessage("no context", "no context");
    assertMessage("[CONTEXT foo=true ]", "");
    assertMessage(" [CONTEXT foo=true ]", "");
    assertMessage("\n[CONTEXT foo=true ]", "");
    assertMessage("message [CONTEXT foo=true ]", "message");
    assertMessage("message\n[CONTEXT foo=true ]", "message");
    // Don't be fooled by embedded context marker.
    assertMessage("message [CONTEXT foo=\" [CONTEXT foo=bar ]\" ]", "message");
  }

  @Test
  public void parse_extractsMessage_invalid() {
    assertMessage("[ CONTEXT foo=true ]", "[ CONTEXT foo=true ]");
    assertMessage("[context foo=true ]", "[context foo=true ]");
    assertMessage("[ CONTEXT ]", "[ CONTEXT ]");
  }

  @Test
  public void parse_extractsMetadata_success() {
    assertMetadata("ignored [CONTEXT foo ]", "foo", null);
    assertMetadata("ignored [CONTEXT foo=true ]", "foo", true);
    assertMetadata("ignored [CONTEXT foo=false ]", "foo", false);
    assertMetadata("ignored [CONTEXT foo=1234 ]", "foo", 1234L);
    assertMetadata("ignored [CONTEXT foo=12.34 ]", "foo", 12.34D);
    assertMetadata(
        "ignored [CONTEXT foo=true bar=10 baz=3.1415926 ]",
        "foo",
        true,
        "bar",
        10L,
        "baz",
        3.1415926D);
    assertMetadata("ignored [CONTEXT foo=\"true\" ]", "foo", "true");
    assertMetadata("ignored [CONTEXT foo=\"xxx\\\\yyy\\nzzz\" ]", "foo", "xxx\\yyy\nzzz");
  }

  @Test
  public void parse_extractsMetadata_badValues() {
    // Unquoted value that cannot be parsed as boolean, double or long.
    assertMetadata("ignored [CONTEXT foo=truX ]", "foo", "truX");
    // Only \n, \r and \t are allowed for quoting but other "quoted" values are handled leniently..
    assertMetadata("ignored [CONTEXT foo=\"foo \\x bar\" ]", "foo", "foo x bar");

    // Drop remaining keys because of unquoted trailing backslash in JSON string.
    assertNoMetadataKeys("ignored [CONTEXT foo=\"trailing backslash\\\" bar=1 ]", "foo", "bar");
    // Drop remaining keys because of missing closing quote.
    assertNoMetadataKeys("ignored [CONTEXT foo=\"no closing quote bar=1 ]", "foo", "bar");
  }

  private void assertMessage(String message, String expected) {
    assertThat(DefaultFormatMetadataParser.parse(message).message()).isEqualTo(expected);
  }

  private void assertMetadata(String message, Object... kvp) {
    checkArgument((kvp.length & 1) == 0);
    ImmutableMap<String, ImmutableList<Object>> metadata =
        DefaultFormatMetadataParser.parse(message).metadata();
    for (int i = 0; i < kvp.length; i += 2) {
      ImmutableList<Object> values = metadata.get((String) kvp[i]);
      assertWithMessage("no metadata key: %s", kvp[i]).that(values).isNotNull();
      Object expected = kvp[i + 1];
      if (expected != null) {
        assertThat(values).contains(expected);
      }
    }
  }

  private void assertNoMetadataKeys(String message, String... keys) {
    ImmutableMap<String, ImmutableList<Object>> metadata =
        DefaultFormatMetadataParser.parse(message).metadata();
    for (String key : keys) {
      assertThat(metadata.keySet()).doesNotContain(key);
    }
  }
}
