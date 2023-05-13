package net.goui.flogger.testing.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopedLoggingContext.LoggingContextCloseable;
import com.google.common.flogger.context.ScopedLoggingContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.goui.flogger.testing.core.LogInterceptor.Recorder;
import net.goui.flogger.testing.jdk.JdkLogInterceptor;
import net.goui.flogger.testing.truth.LogSubject;
import net.goui.flogger.testing.truth.LogsSubject;
import org.checkerframework.checker.nullness.qual.Nullable;

/** One of these is instantiated per test case. */
public class TestApi {
  private final ImmutableMap<String, ? extends Level> levelMap;
  private LogInterceptor interceptor;
  private final Consumer<TestApi> commonAssertions;

  protected TestApi(
      Map<String, ? extends Level> levelMap,
      @Nullable LogInterceptor interceptor,
      @Nullable Consumer<TestApi> commonAssertions) {
    this.levelMap = ImmutableMap.copyOf(levelMap);
    this.interceptor = interceptor;
    this.commonAssertions = commonAssertions;
  }

  private ImmutableList<LogEntry> logged() {
    return interceptor.getLogs();
  }

  public LogSubject assertLog(int n) {
    return LogsSubject.assertThat(logged()).get(n);
  }

  public LogsSubject assertThat() {
    return LogsSubject.assertThat(logged());
  }

  protected final ImmutableMap<String, ? extends Level> levelMap() {
    return levelMap;
  }

  protected final LogInterceptor interceptor() {
    return interceptor;
  }

  protected final Consumer<TestApi> commonAssertions() {
    return commonAssertions;
  }

  protected final ApiHook install() {
    return new ApiHook();
  }

  public final class ApiHook implements AutoCloseable {
    private final List<Recorder> recorders = new ArrayList<>();
    private final LoggingContextCloseable context;

    private ApiHook() {
      if (interceptor == null) {
        interceptor = loadBestInterceptor();
      }
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
      commonAssertions.accept(TestApi.this);
    }
  }

  private LogInterceptor loadBestInterceptor() {
    return JdkLogInterceptor.create();
  }
}
