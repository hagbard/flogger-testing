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

import static com.google.common.flogger.LogContext.Key.LOG_CAUSE;
import static com.google.common.flogger.LogContext.Key.WAS_FORCED;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.TemplateContext;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A simple implementation of LogData, that's functional enough for testing Flogger support levels.
 * This is only intended for use by {@link FloggerBinding} to avoid needing to instantiate an
 * instance of FluentLogger directly.
 */
final class SimpleLogData implements LogData {
  private static final Object[] NO_ARGS = new Object[0];

  private final String backendName;
  private final LogSite logSite;
  private final Level level;
  private final String message;
  private final Metadata metadata;
  private final boolean wasForced;
  private final long timestampNanos;

  SimpleLogData(
      String name,
      LogSite logSite,
      Level level,
      String message,
      Throwable thrown,
      boolean wasForced) {
    this.backendName = requireNonNull(name);
    this.logSite = logSite;
    this.level = requireNonNull(level);
    this.message = requireNonNull(message);
    this.timestampNanos = MILLISECONDS.toNanos(System.currentTimeMillis());
    this.wasForced = wasForced;
    ImmutableMap.Builder<MetadataKey<?>, Object> metadata = ImmutableMap.builder();
    if (thrown != null) {
      metadata.put(LOG_CAUSE, thrown);
    }
    if (wasForced) {
      metadata.put(WAS_FORCED, true);
    }
    this.metadata = new SimpleMetadata(metadata.build());
  }

  @Override
  public Level getLevel() {
    return level;
  }

  @Override
  public long getTimestampMicros() {
    return NANOSECONDS.toMicros(timestampNanos);
  }

  @Override
  public long getTimestampNanos() {
    return timestampNanos;
  }

  @Override
  public String getLoggerName() {
    return backendName;
  }

  @Override
  public Object getLiteralArgument() {
    return message;
  }

  @Override
  public Metadata getMetadata() {
    return metadata;
  }

  @Override
  public LogSite getLogSite() {
    return logSite;
  }

  @Override
  public boolean wasForced() {
    return wasForced;
  }

  @Override
  public TemplateContext getTemplateContext() {
    return null;
  }

  @Override
  public Object[] getArguments() {
    return NO_ARGS;
  }

  private static class SimpleMetadata extends Metadata {
    ImmutableMap<MetadataKey<?>, Object> map;

    private SimpleMetadata(ImmutableMap<MetadataKey<?>, Object> map) {
      this.map = requireNonNull(map);
    }

    @Override
    public int size() {
      return map.size();
    }

    @Override
    public MetadataKey<?> getKey(int n) {
      return map.keySet().asList().get(n);
    }

    @Override
    public Object getValue(int n) {
      return map.values().asList().get(n);
    }

    @Nullable
    @Override
    public <T> T findValue(MetadataKey<T> key) {
      return key.cast(map.get(key));
    }
  }
}
