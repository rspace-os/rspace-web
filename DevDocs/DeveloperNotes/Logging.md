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

#### Calendar subscription bearer URLs

`/public/booking/calendars/feed.ics` accepts a calendar-subscription bearer in
the `token` query parameter. Treat the complete subscription URL like a
password. The application skips generic request logging for this route and its
slow-request logger records only the fixed request path, but container,
reverse-proxy, and observability access logs must also omit the query string or
redact `token` for this route before the feature is released.

For Tomcat's `AccessLogValve`, use path-only fields such as `%m %U %H` and do
not use `%r` or `%q`: `%U` is the requested URL path, while `%q` is the query
string and `%r` is the request line. For example:

```xml
pattern='%h %l %u %t "%m %U %H" %s %b'
```

For nginx, build the access-log request field from `$request_method $uri
$server_protocol`. Do not use `$request` or `$request_uri` for this route,
because they include the original request arguments. An installation that
needs query strings for other traffic must select a route-specific log format
for `/public/booking/calendars/feed.ics` or apply verified field-level
redaction before records leave the proxy.

The Docker development stack does not configure a Jetty request log. If one is
enabled locally or in another supported deployment, configure it to record the
path only (or verified route-scoped query redaction), never the complete
request target. Apply the same rule to APM agents, WAFs, load balancers, CDN
logs, traces, analytics, and support tooling. If a supported deployment cannot
meet this requirement, calendar subscriptions must remain disabled there.

References: the
[Tomcat AccessLogValve pattern fields](https://tomcat.apache.org/tomcat-10.0-doc/config/valve.html#Access_Log_Valve)
and [nginx request variables](https://nginx.org/en/docs/http/ngx_http_core_module.html#variables).

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
