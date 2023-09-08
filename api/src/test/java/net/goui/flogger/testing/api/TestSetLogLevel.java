package net.goui.flogger.testing.api;

import java.lang.annotation.Annotation;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.SetLogLevel;

class TestSetLogLevel implements SetLogLevel {
  static SetLogLevel of(Class<?> target, LevelClass level) {
    return of(target, "", Scope.UNDEFINED, level);
  }

  static SetLogLevel of(String name, LevelClass level) {
    return of(Object.class, name, Scope.UNDEFINED, level);
  }

  static SetLogLevel of(Scope scope, LevelClass level) {
    return of(Object.class, "", scope, level);
  }

  static SetLogLevel of(Class<?> target, String name, Scope scope, LevelClass level) {
    return new TestSetLogLevel(target, name, scope, level);
  }

  private final Class<?> target;
  private final String name;
  private final Scope scope;
  private final LevelClass level;

  private TestSetLogLevel(Class<?> target, String name, Scope scope, LevelClass level) {
    this.target = target;
    this.name = name;
    this.scope = scope;
    this.level = level;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return TestSetLogLevel.class;
  }

  @Override
  public Class<?> target() {
    return target;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Scope scope() {
    return scope;
  }

  @Override
  public LevelClass level() {
    return level;
  }
}
