package com.researchspace.webapp.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a controller method with this annotation if automated logging should not be performed
 * for this method. <br>
 * By default, all HTTP requests should be logged, but there may be reasons for exceptions:
 *
 * <ul>
 *   <li>The method is called very frequently (e.g., polling, image retrieval ) and logging does not
 *       record any useful information
 *   <li>The method parameters are sensitive - e.g., user authentication credentials
 * </ul>
 *
 * Usage:
 *
 * <table border=1 style="border-collapse:collapse;margin:10px">
 * <tr>
 * <th>Use</th>
 * <th>Effect</th>
 * </tr>
 * <tr>
 * <td>No annotation</td>
 * <td>Methods invocations and all HttpRequest params are logged.</td>
 * </tr>
 * <tr>
 * <td>@IgnoreInLoggingInterceptor</td>
 * <td>As for no annotation - everything is logged. This means that all uses of this annotation need
 * to have at least one attribute set.</td>
 * </tr>
 * <tr>
 * <td>@IgnoreInLoggingInterceptor(ignoreAll=true)</td>
 * <td>Nothing is logged. This overrides all other attributes of the annotation.</td>
 * </tr>
 * <tr>
 * <td>@IgnoreInLoggingInterceptor(ignoreAllRequestParams=true)</td>
 * <td>Method invocations are logged, but no HttpRequest params are logged. This is a convenience
 * attribute in the case that all request parameters are sensitive.</td>
 * </tr>
 * <tr>
 * <td>@IgnoreInLoggingInterceptor(ignoreRequestParams={"sensitiveParam"})</td>
 * <td>Method invocations are logged, and all HttpRequest params, <em>excluding</em> those added as
 * a list in this attribute. This is useful where some HttpRequest params should be logged, and some
 * not.</td>
 * </tr>
 * </table>
 *
 * For examples, look for references in the codebase, especially in SysadminController where there
 * are several methods requiring re-authentication, and we don't want to log the passwords.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreInLoggingInterceptor {

  /**
   * If set to true, the controller method invocation will be recorded, but not the request
   * parameters. This is useful for logging events that contain sensitive information, but where we
   * still want to record the fact that the method was called.
   *
   * @return <code>true</code> if the method call should be logged, without request params, or
   *     <code>false</code>( default) if the method should not be logged at all.
   */
  boolean ignoreAllRequestParams() default false;

  /**
   * All logging is ignored for this request. Default is <code>false</code>.
   *
   * @return
   */
  boolean ignoreAll() default false;

  /**
   * Ignore a subset of parameters - e.g., sensitive ones such as username ,password etc. Default is
   * an empty array
   *
   * @return
   */
  String[] ignoreRequestParams() default {};
}
