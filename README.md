# Flogger Logs Testing API

A powerful fluent API for testing Flogger log statements.

#### Easy to Install

<!-- @formatter:off -->
```xml
<dependency>
    <groupId>net.goui.flogger.testing</groupId>
    <artifactId>junit4</artifactId>  <!-- or junit5 -->
    <version>1.0.0</version>
</dependency>
```
<!-- @formatter:on -->

And if you are using `Log4J2`:
<!-- @formatter:off -->
```xml
<dependency>
    <groupId>net.goui.flogger.testing</groupId>
    <artifactId>log4j</artifactId>
    <version>1.0.0</version>
</dependency>
```
<!-- @formatter:on -->

#### Easy to Setup

JUnit 4
<!-- @formatter:off -->
```java
@RunWith(JUnit4.class)
public class MyJUnit4Test {
  @Rule
  public final FloggerTestRule logs = FloggerTestRule.forClassUnderTest(INFO);

  // Tests ...
}
```
<!-- @formatter:on -->

JUnit 5
<!-- @formatter:off -->
```java
public class MyJUnit5Test {
  @RegisterExtension
  public final FloggerTestExtension logs = FloggerTestExtension.forClassUnderTest(INFO);

  // Tests ...
}
```
<!-- @formatter:on -->

#### Easy to Use

<!-- @formatter:off -->
```java
logs.assertLogs().withMessageContaining("Expected log message").matchCount().isAtLeast(1);
```
<!-- @formatter:on -->

#### A Powerful and Robust API

<!-- @formatter:off -->
```java
var debugStart = logs.assertLogs()
    .withMessageContaining("Start debug")
    .withMetadata("debug_id",TEST_ID)
    .getOnlyMatch();
assertLogs(after(debugStart).inSameThread()).always().haveMetadata("debug_id", TEST_ID);
```
<!-- @formatter:on -->

## Introduction

The testing of debug log statements and logging behaviour has never really has a single,
comprehensive, testing API.
In many cases users end up writing brittle assertions in unit tests (e.g. by assuming that log
statements correspond to
specific indices within some list). The usually results in users:

* Writing brittle tests for their logs, which creates an ongoing maintenance burden.
* Writing few or no tests for their logs, which risks severe logging issues going unnoticed.
* Writing logs tests with ad-hoc logger specific APIs, which complicates any work to update or
  migrate logging
  libraries.

We've all seen at least one (if not all) the situations described above.

As Flogger's creator and API designer, I decided to do something about it for Flogger users, but I
didn't want to stop
there, so I made a library which can work with any logging API.

## Why Testing Logs Properly is Hard

While at Google, I worked extensively with Java code to improve debug logging. I've seen many
thousands of log statements, and the unit tests which attempt to make assertions about them.

Over this time I came to realize that there is no "one size fits all" approach to testing debug
logs.

* What one team cared deeply about, another was happy to ignore.
* While one team wanted to test a lot of details about what was logged, another wanted to
  make assertions about what wasn't logged.

Good logs testing ends up being a combination of several "best practice" principles, and those
principles need to be supported properly in a good logs testing API:

### Principle: Test only the logs you care about.

In order to test logs at all, you need to know which logs are in scope for testing.
Typically, but not always, these are the logs created by the code-under-test.

Most of the time you should avoid testing logs generated by code outside the `class` (or at least
`package`) you are testing. Creating implicit test dependencies on the logging behaviour of other
classes is "spooky action at a distance", and can easily result in hard to diagnose test failures.

This principle is supported with the following features:

#### Intuitive Scoping of log capture for tests

Convenience methods for the commonest cases:

```java
public final FloggerTestExtension logs=FloggerTestExtension.forClassUnderTest(INFO);
```

```java
public final FloggerTestExtension logs=FloggerTestExtension.forPackageUnderTest(INFO);
```

Flexible methods for unusual situations:

```java
public final FloggerTestExtension logs=FloggerTestExtension.forLevelMap(LEVEL_MAP);
```

#### Powerful filtering of captured logs in assertions

Matching on attributes of log entries:

```java
logs.assertLogs().withLevelAtLeast(WARNING)...
```

Comparative filtering relative to other log entries:

```java
logs.assertLogs(after(otherEntry))...
```

```java
logs.assertLogs(isSameThreadAs(otherEntry))...
```

### Principle: Don't over-test logging.

While it's tempting to test for the exact contents of a log statement (after all, you can just
"cut & paste" the log message into the test from the code or logs), it's generally a bad idea.

Debug log messages are expected to be human-readable, and you need to be able to update them as
needed during debugging. If you improve the message in a not-very-readable log statement, you
don't want to break dozens of tests. You need to test the important information in log
statements, while avoiding testing grammar or punctuation.

You may choose to explicitly avoid testing the details of "fine" log statements in your code, or at
least test them less. Fine logs exist to provide content to other logs, and they are more likely to
be added, removed or otherwise modified during debugging. Don't make this break all your existing
logging tests.

However, it's also important that you execute "fine" log statements in some way during at least
some tests. Otherwise, you risk discovering that your log statements aren't working (e.g.
because a `toString()` method is throwing an exception) at exactly the time you need them most.

This principle is supported with the following features:

#### Targeted assertion APIs

### Principle: Test that unwanted logs do not occur.

The other principle of logs testing (which is often ignored) is to ensure that unwanted logs,
perhaps from other libraries, are not caused by your code.

This is quite a different use case and seems, at first, to violate the principle of testing only
logs under your control. However, testing that a log does not occur is far, far less brittle
assertion than testing something about it.

Unwanted logs are typically only high-severity logs ("warning", "severe" etc.) which are likely
to cause issues if they start appearing, so it's valuable to make sure your code isn't
triggering something by mistake. And, in cases where your code _is_ expected to cause a
high-severity log from another library, that's an example of a time when it might be acceptable
to test it directly.

This principle is supported with the following features:

#### Simple exclusion of log entries

#### Easy to control post-test verification

### Principle: Don't Inject Mock Loggers for Testing

(and perhaps more strongly "Don't Inject Loggers into your code at all")

A common, but in my opinion undesirable, pattern is to inject a mock logger in unit tests for
testing logs. This mimics the use of mocks for testing other aspects of code, and is often easy when
you've already got a dependency injection system set up. However, it comes with several non-trivial
downsides.

1. **Injecting logger implementations creates maintenance burden.** Teams cannot migrate easily to
   different logging APIs, and in a large codebase you eventually end up injecting multiple logger
   implementations simultaneously to satisfy the needs of all your code.
2. **Injecting logger implementations encourages bad logging habits.** An injected logger
   cannot be used in static methods without passing it in explicitly. Passing in a logger
   instance breaks encapsulation, especially for non-private methods, and not allowing debug
   logging in static methods is limiting.
3. **Testing the log statement (via a mock) is less useful than testing the log output.** Log
   output is what's useful about debug log statements, so you should be testing that as closely as
   reasonably possible. Using mocks to test fine-grained logging APIs creates brittle, and hard
   to maintain tests.
4. **Injecting logger implementations is never necessary.** All commonly used logging libraries
   provide a way to test their log output at some reasonable level. This ensures that what you
   test is "close" to what will appear in log files.

I think that the difficulty in setting up good logs testing in unit tests is one of the primary
reasons people chose to inject loggers in their code at all. With a well-designed logs testing
library, this problem no longer exists, and so injecting loggers at all no longer has any real
benefits.

This principle is supported in the core design of the library, which is agnostic to the
underlying logger implementation used and carefully designed to minimize implicit dependencies
on specific implementation behaviour.

If you use this library and wish to migrate your logging (e.g. from Log4J to Flogger, or JUnit4 
to JUnit5), your tests will remain essentially unchanged.

## Summary

The Flogger logs testing API supports the above principles with a clean, orthogonal, fluent API
which can be used with JDK's logging libraries or `Log4J2`, and in either `JUnit4` or `Junit5`.

Pick the dependencies which suit your needs, and start writing better logging tests today!