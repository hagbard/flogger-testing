package net.goui.flogger.testing.core;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

@AutoValue
public abstract class MessageAndMetadata {
  public abstract String message();

  public abstract ImmutableMap<String, ImmutableList<Object>> metadata();

  public static MessageAndMetadata of(String message, Map<String, List<Object>> metadata) {
    ImmutableMap.Builder<String, ImmutableList<Object>> map = ImmutableMap.builder();
    metadata.forEach((k, v) -> map.put(k, ImmutableList.copyOf(v)));
    return new AutoValue_MessageAndMetadata(message, map.build());
  }
}
