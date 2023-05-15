package net.goui.flogger.testing.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopedLoggingContext.LoggingContextCloseable;
import com.google.common.flogger.context.ScopedLoggingContexts;
import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.LogInterceptor.Recorder;
import net.goui.flogger.testing.truth.LogSubject;
import net.goui.flogger.testing.truth.LogsSubject;
import org.checkerframework.checker.nullness.qual.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/** One of these is instantiated per test case. */
public class TestApi {
  private final ImmutableMap<String, ? extends Level> levelMap;
  private LogInterceptor interceptor;
  @Nullable private final Consumer<TestApi> commonAssertions;

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

  protected static String loggerNameOf(Class<?> clazz) {
    checkArgument(
        !clazz.isArray(), "Invalid class for log capture (must not be an array): %s", clazz);
    checkArgument(
        !clazz.isAnonymousClass(),
        "Invalid class for log capture (must not be anonymous): %s",
        clazz);
    checkArgument(
        !clazz.isPrimitive(),
        "Invalid class for log capture (must not be an primitive): %s",
        clazz);
    checkArgument(
        !clazz.isSynthetic(), "Invalid class for log capture (must not be synthetic): %s", clazz);
    String className = clazz.getCanonicalName();
    checkNotNull(className, "target class does not have a canonical name: %s", clazz);
    return className.replace('$', '.');
  }

  public LogSubject assertLog(int n) {
    return Truth.assertWithMessage("failure for log[%s]", n)
        .about(LogSubject.logEntries())
        .that(logged().get(n));
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
        interceptor = BestInterceptorFactory.get();
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
      if (commonAssertions != null) {
        commonAssertions.accept(TestApi.this);
      }
    }
  }

  // Lazy holder for caching the loaded interceptor.
  private static final class BestInterceptorFactory {
    private static final Supplier<LogInterceptor> factory =
        LogInterceptorLoader.loadBestInterceptorFactory();

    static LogInterceptor get() {
      return factory.get();
    }
  }
}
