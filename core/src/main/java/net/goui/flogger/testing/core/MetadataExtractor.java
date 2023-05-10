package net.goui.flogger.testing.core;

public interface MetadataExtractor {
  MessageAndMetadata parse(String formattedMessage);
}
