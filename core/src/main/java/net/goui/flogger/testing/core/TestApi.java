package net.goui.flogger.testing.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopedLoggingContext.LoggingContextCloseable;
import com.google.common.flogger.context.ScopedLoggingContexts;
import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import net.goui.flogger.testing.core.LogInterceptor.Recorder;

public class TestApi {
  private final ImmutableMap<String, ? extends Level> levelMap;
  private final LogInterceptor interceptor;

  protected TestApi(Map<String, ? extends Level> levelMap, Optional<LogInterceptor> interceptor) {
    this.levelMap = ImmutableMap.copyOf(levelMap);
    this.interceptor = interceptor.orElseGet(this::loadBestInterceptor);
  }

  public LogSubject assertLog(int n) {
    return Truth.assertWithMessage("log(%s)", n)
        .about(LogSubject.logs())
        .that(interceptor.getLogs().get(n));
  }

  public LogStreamSubject assertLogs() {
    return LogStreamSubject.assertThat(interceptor.getLogs());
  }

  protected final ApiHook install() {
    return new ApiHook();
  }

  public final class ApiHook implements AutoCloseable {
    private final List<Recorder> recorders = new ArrayList<>();
    private final LoggingContextCloseable context;

    private ApiHook() {
      levelMap.forEach((name, level) -> recorders.add(interceptor.attachTo(name, level)));
      context =
          ScopedLoggingContexts.newContext()
              .withLogLevelMap(LogLevelMap.create(levelMap))
              .install();
    }

    @Override
    public void close() {
      context.close();
      recorders.forEach(Recorder::close);
    }
  }

  private LogInterceptor loadBestInterceptor() {
    return new JdkLogInterceptor();
  }
}
