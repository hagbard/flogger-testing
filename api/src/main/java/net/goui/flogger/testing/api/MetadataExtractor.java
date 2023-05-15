package net.goui.flogger.testing.api;

public interface MetadataExtractor<T> {
  MessageAndMetadata extract(T source);
}
