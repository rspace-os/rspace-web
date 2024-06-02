# Running RS over a secure connection.

This document summarises how and why to configure secure connections,
and the relevant files

## Configuring the application

RS uses Apache Shiro security library, and the security configuration is
in `WEB-INF/security.xml`. Configuring ssl is very easy - just add the
filter 'ssl' to any URL paths that you want to run over HTTPS to the
list of filterChainDefinitions. You need to specify the port number.
Currently, this is 8443, the default HTTPS port.

There is a switch that enables/disables a Shiro filter, so that for
example, the ssl filter can be enabled in production but disabled during
development. This is set up by overriding the default filter instance in
the 'filters' property. We have set this up to use Maven Resource
filtering, so that ssl can be enabled from a build (more on this below)

## Configuring Jetty

For development, we run on jetty localhost, and normally running over
HTTP is fine for development purposes. This is the default.

Jetty 10 is configured to run over HTTPS (using HTTP2) if need be though.

Since Jetty 9, this can't be configured in Maven Jetty plugin anymore,
but needs specific Jetty configuration files. These are in the project root /jetty
folder and are adapted from config files here http://juplo.de/configure-https-for-jetty-maven-plugin-9-0-x/#comment-53736
with reference to the jetty docs https://eclipse.dev/jetty/documentation/jetty-10/operations-guide/index.html#og-xml-syntax

To run, run with flag `-Dssl.enabled=true`.

For Shiro, in `security.xml`:
```
<property name="enabledOverride" value="${ssl.enabled}"/>
```
to
```
<property name="enabledOverride" value="true"/>
```

For `jetty:run`, this needs to be hardcoded, as the resource filtering and
replacement of the variable `${ssl.enabled}` only works when filtering
files in the target/ folder - but `jetty:run` runs the webapp from the
source file.

Later, when you switch back to non-SSL mode and try login into RSpace
http port (8080) with the same browser, you may see your login
credentials not working (happens in Chrome). Remove the browser's
cookies stored for localhost address to fix it.

## Useful tips

use HttpServletRequest.isSecure() as the best way to test if a
connection is secure, if we need to.

## Useful references

- http://stackoverflow.com/questions/8200853/how-can-i-know-if-the-request-to-the-servlet-was-executed-using-http-or-https
- Configuring Jetty/Shiro for HTTPS
  http://raibledesigns.com/rd/entry/java_web_application_security_part2
- Apache Shiro config
  http://shiro.apache.org/web.html
