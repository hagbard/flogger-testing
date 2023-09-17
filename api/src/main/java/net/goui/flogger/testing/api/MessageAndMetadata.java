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
