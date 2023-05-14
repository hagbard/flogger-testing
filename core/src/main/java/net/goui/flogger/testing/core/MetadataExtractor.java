package net.goui.flogger.testing.core;

public interface MetadataExtractor<T> {
  MessageAndMetadata extract(T source);
}
