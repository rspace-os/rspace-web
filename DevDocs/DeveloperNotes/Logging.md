# Logging

This file describes how and where logging is performed in RSpace.

## Basics

We use log4j2 implementation behind slf4j interface, whose configuration
is in `src/main/resources/log4j2.xml`

## Built-in logging

Several mechanisms are in place to automatically log certain events

### AOP loggers

`ServiceLoggerAspect.java` defines Aspects that will wrap Service or DAO
classes with loggers, that are enabled at various log-levels.
Current loggers include:
- slow service methods
- DB exceptions
- any service method call (at DEBUG level)

### Security Logging

Logs for authentication/authorisation errors are in `SecurityEvents.txt`.

### Incoming requests

These are logged using the interceptor `LoggingInterceptor.java` and
logged to `RSLogs.txt`.

## Basic custom logging

Add `log.info`, `log.warn`, `log.error` statements in code as appropriate.
These will get logged to the console (or `catalina.out` on Tomcat)

Use
```
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
```
to obtain log instances for general logging in any new classes. 
Where LoggerFactory is an `org.slf4j.Logger` instance which should
define an `org.slf4j.Logger` instance.

We're still using log4j under the hood, but by switching to slf4j API
mechanism we can switch implementations (e.g., to logback) more easily
in the future.

A nice thing about slf4j is that you can parameterise messages with {}
placeholders e.g.,

```
log.info(
  "Unauthorised query by user [{}]: to [{}] - {}",
  SecurityUtils.getSubject().getPrincipal(),
  request.getRequestURI(),
  e.getMessage());
```
which reduces the ugly `String` concatenation and means that `Strings`
aren't evaluated unless the level is appropriate.

Since `1.40` we've started using Lombok, which is a library that adds in
boilerplate code; it has an `@Slf4j` annotation, which configures a logger
without requiring code, useful for adding loggers with default behaviour.

Using slf4j API will be useful for any RSpace Java API libraries we
write in the future, since they don't impose a logging implementation on
the client using it.
