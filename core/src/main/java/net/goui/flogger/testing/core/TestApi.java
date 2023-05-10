package net.goui.flogger.testing.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopedLoggingContext.LoggingContextCloseable;
import com.google.common.flogger.context.ScopedLoggingContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import net.goui.flogger.testing.core.LogInterceptor.Recorder;
import net.goui.flogger.testing.core.truth.LogEntry;
import net.goui.flogger.testing.core.truth.LogEntrySubject;
import net.goui.flogger.testing.core.truth.LogSubject;
import net.goui.flogger.testing.jdk.JdkLogInterceptor;

/** One of these is instantiated per test case. */
public class TestApi {
  private final ImmutableMap<String, ? extends Level> levelMap;
  private final LogInterceptor interceptor;

  protected TestApi(Map<String, ? extends Level> levelMap, Optional<LogInterceptor> interceptor) {
    this.levelMap = ImmutableMap.copyOf(levelMap);
    this.interceptor = interceptor.orElseGet(this::loadBestInterceptor);
  }

  private ImmutableList<LogEntry> logged() {
    return interceptor.getLogs();
  }

  public LogEntrySubject assertLog(int n) {
    return LogSubject.assertThat(logged()).log(n);
  }

  public LogSubject assertLogs() {
    return LogSubject.assertThat(logged());
  }

  protected final ImmutableMap<String, ? extends Level> levelMap() {
    return levelMap;
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
    return JdkLogInterceptor.create();
  }
}
