package com.researchspace.webapp.controller;

import com.researchspace.licensews.LicenseExpiredException;
import com.researchspace.service.LicenseService;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Aspect for general logging. */
@Aspect
@Component
public class ServiceLoggerAspct {

  private static final Logger log = LoggerFactory.getLogger(ServiceLoggerAspct.class);

  @Value("${slow.transaction.time:1000}")
  private Integer slowTransactionTimeThreshold;

  private final int maxPrintedArgLength = 100;

  @Autowired private LicenseService licenseService;

  /**
   * @param licenseService the licenseService to set
   */
  public void setLicenseService(LicenseService licenseService) {
    this.licenseService = licenseService;
  }

  // public methods in service API
  @Pointcut("execution(public * com.researchspace.service.*.*(..))")
  private void serviceRequest() {}

  // public DAO methods
  @Pointcut("execution(public * com.researchspace.dao.*.*(..))")
  private void publicDaoMethod() {}

  // public methods annotated with @RequiresActiveLicense
  @Pointcut("execution(@com.researchspace.service.RequiresActiveLicense * *.*(..))")
  private void checkLicense() {}

  /**
   * @param joinPoint
   */
  @Before("checkLicense()")
  public void assertValidLicense(JoinPoint joinPoint) {
    if (!licenseService.isLicenseActive()) {
      log.warn(
          "License expired! Attempt to access {}  with an invalid license.",
          joinPoint.getSignature());
      // which prevents the the method from executing.
      throw new LicenseExpiredException(
          "Could not perform this operation as the license has expired");
    }
  }

  // logs database / DAO exceptions
  @AfterThrowing(pointcut = "publicDaoMethod()", throwing = "ex")
  public void doRecoveryActions(Throwable ex) {
    // logger = LoggerFactory.getLogger(ex.getClass());
    log.warn("Database exception!- {}", ex.getMessage());
  }

  // logs entry to service requests with runtime method arguments.
  @Around("serviceRequest()")
  public Object doAccessCheckAndProfile(ProceedingJoinPoint jp) throws Throwable {
    long start = System.currentTimeMillis();

    String methodName = jp.getSignature().getName();
    String methodInfoStringForLogging = methodInfo(jp, methodName);
    log.debug(methodInfoStringForLogging);

    Object rc = jp.proceed();
    long elapsedTime = System.currentTimeMillis() - start;

    log.debug(
        "ServiceLogger.profile(): Method [{}] execution time: {} ms.", methodName, elapsedTime);

    if (elapsedTime > slowTransactionTimeThreshold) {
      log.warn(
          "Execution time longer than {} ms ({} ms). {}",
          slowTransactionTimeThreshold,
          elapsedTime,
          methodInfoStringForLogging);
    }
    return rc;
  }

  protected String methodInfo(ProceedingJoinPoint jp, String methodName) {
    MethodSignature methodSignature = (MethodSignature) jp.getSignature();
    if (shouldSkipMethodArgs(methodSignature)) {
      return String.format(
          "In method [%s] in class [%s] (args hidden)",
          methodName, jp.getSignature().getDeclaringTypeName());
    }
    return String.format(
        "In method [%s] in class [%s] with args: %s",
        methodName,
        jp.getSignature().getDeclaringTypeName(),
        getTruncatedArgumentString(maxPrintedArgLength, jp.getArgs()));
  }

  private boolean shouldSkipMethodArgs(MethodSignature methodSignature) {
    IgnoreInServiceLoggerAspct ignoreLoggingAnnotation =
        methodSignature.getMethod().getAnnotation(IgnoreInServiceLoggerAspct.class);
    return ignoreLoggingAnnotation != null && ignoreLoggingAnnotation.ignoreAllRequestParams();
  }

  protected String getTruncatedArgumentString(final int maxLength, Object[] args) {
    if (args == null) {
      return null;
    }
    StringBuffer buf = new StringBuffer();

    for (Object arg : args) {
      if (arg == null) {
        buf.append("[null]");
      } else {
        buf.append(StringUtils.abbreviate(arg.toString(), maxLength));
      }
    }
    return buf.toString();
  }
}
