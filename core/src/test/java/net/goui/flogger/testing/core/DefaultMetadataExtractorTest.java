package net.goui.flogger.testing.core;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultMetadataExtractorTest {
  MetadataExtractor extractor = new DefaultFormatMetadataExtractor();

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
    assertMetadata("ignored [CONTEXT foo=true bar=10 baz=3.1415926 ]", "foo", true, "bar", 10L, "baz", 3.1415926D);
    assertMetadata("ignored [CONTEXT foo=\"true\" ]", "foo", "true");
    assertMetadata("ignored [CONTEXT foo=\"xxx\\\\yyy\\nzzz\" ]", "foo", "xxx\\yyy\nzzz");
  }

  private void assertMessage(String message, String expected) {
    assertThat(extractor.parse(message).message()).isEqualTo(expected);
  }

  private void assertMetadata(String message, Object... kvp) {
    checkArgument((kvp.length & 1) == 0);
    ImmutableMap<String, ImmutableList<Object>> metadata = extractor.parse(message).metadata();
    for (int i = 0; i < kvp.length; i += 2) {
      ImmutableList<Object> values = metadata.get((String) kvp[i]);
      assertWithMessage("no metadata key: %s", kvp[i]).that(values).isNotNull();
      Object expected = kvp[i + 1];
      if (expected != null) {
        assertThat(values).contains(expected);
      }
    }
  }
}
