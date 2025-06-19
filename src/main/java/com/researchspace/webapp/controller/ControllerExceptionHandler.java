package com.researchspace.webapp.controller;

import com.researchspace.core.util.DateUtil;
import com.researchspace.core.util.LoggingUtils;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.SecurityLogger;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Default handler for exceptions. This class should be wired into controller classes, and delegated
 * to from within an @ExceptionHandler annotated method.
 *
 * @see IControllerExceptionHandler
 */
public class ControllerExceptionHandler implements IControllerExceptionHandler {

  public static final String EXCEPTION_MESSAGE_ATTR_NAME = "exceptionMessage";

  public static final String AJAX_DEFAULT_ERROR_MSG = "Something went wrong: ";

  public static final String NON_AJAX_ERROR_VIEW_NAME = "error";

  public static final String AJAX_ERROR_VIEW_NAME = "ajaxError";

  private static final Logger log = LoggerFactory.getLogger(ControllerExceptionHandler.class);
  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private static final int DEFAULT_MSG_LENGTH = 200;

  private int exceptionMessageStringLength = DEFAULT_MSG_LENGTH;

  public int getExceptionMessageStringLength() {
    return exceptionMessageStringLength;
  }

  public void setExceptionMessageStringLength(int exceptionMessageStringLength) {
    this.exceptionMessageStringLength = exceptionMessageStringLength;
  }

  /**
   * Logs exceptions, stack traces and returns a suitable view for both regular and Ajax based
   * requests. Logs security exceptions to Security log
   */
  public ModelAndView handleExceptions(
      HttpServletRequest request, HttpServletResponse response, Exception e) {
    // handle authentication/authorisation exceptions differently.
    String tstamp = DateUtil.convertDateToISOFormat(new Date(), TimeZone.getDefault());
    String errorId = LoggingUtils.generateLogId();

    if (isSecurityException(e)) {
      log.warn("Handling security-related exception {}: {}", errorId, e.getMessage());
      SECURITY_LOG.warn(
          "Security-related exception [{}] by user [{}] to [{}]: {}",
          errorId,
          SecurityUtils.getSubject().getPrincipal(),
          request.getRequestURI(),
          e.getMessage());
    } else if (e instanceof PublicLinkNotFoundException) {
      log.warn(
          "The following external public link was not found in this instance of RSpace: "
              + e.getMessage());
      return new ModelAndView("/public/publicLinkNotFound");
    } else {
      if (e instanceof HttpClientErrorException) {
        if (((HttpClientErrorException) e).getStatusCode().is5xxServerError()) {
          logErrorFromException(e, errorId);
        } else {
          logWarningFromException(e, errorId);
        }
      } else if (e instanceof ConstraintViolationException) {
        logWarningFor(getConstraintViolationMessage((ConstraintViolationException) e), errorId);
      } else {
        logErrorFromException(e, errorId);
      }
    }

    String ajaxErrorView = AJAX_ERROR_VIEW_NAME;
    String ajaxDefaultErrorMessage = AJAX_DEFAULT_ERROR_MSG;
    boolean ajaxShowTechMessage = true;
    if (isAjax(request)) {
      String exceptionMessage = ajaxDefaultErrorMessage;
      if (ajaxShowTechMessage) {
        exceptionMessage += "\n" + getExceptionMessage(e);
      }
      Object errors = request.getAttribute("ajax.errors");
      ModelAndView m = new ModelAndView(ajaxErrorView);
      if (errors != null && errors instanceof ErrorList) {
        m.addObject("errors", errors);
      }
      m.addObject(EXCEPTION_MESSAGE_ATTR_NAME, exceptionMessage);
      addTimeStampAndErrorId(tstamp, errorId, m);

      // this triggers an ajax error event in the client.
      setErrorResponseStatus(response, e);
      return m;
    } else {
      ModelAndView m = new ModelAndView(NON_AJAX_ERROR_VIEW_NAME);
      m.addObject(EXCEPTION_MESSAGE_ATTR_NAME, getExceptionMessage(e));
      addTimeStampAndErrorId(tstamp, errorId, m);
      return m;
    }
  }

  private void logErrorFromException(Exception e, String errorId) {
    log.error(
        "Unexpected exception in controller:errorId-[{}]. [{}]. {}",
        errorId,
        e.getMessage(),
        convertStackTraceToString(e));
  }

  private void logWarningFromException(Exception e, String errorId) {
    log.warn(
        "Expected exception type in controller:errorId-[{}]. [{}]. {}",
        errorId,
        e.getMessage(),
        convertStackTraceToString(e));
  }

  private void logWarningFor(String message, String errorId) {
    log.warn("Expected exception type in controller:errorId-[{}]. [{}]. {}", errorId, message);
  }

  private void addTimeStampAndErrorId(String tstamp, String errorId, ModelAndView m) {
    m.addObject("tstamp", tstamp);
    m.addObject("errorId", errorId);
  }

  private void setErrorResponseStatus(HttpServletResponse response, Exception e) {
    if (isSecurityException(e)) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    } else {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private boolean isSecurityException(Exception e) {
    return e instanceof RecordAccessDeniedException
        || e instanceof AuthorizationException
        || e instanceof AuthenticationException;
  }

  private String convertStackTraceToString(Exception e) {
    return Arrays.toString(e.getStackTrace()).replace(",", "\n at");
  }

  private boolean isAjax(HttpServletRequest request) {
    return RequestUtil.isAjaxRequest(request);
  }

  private String getExceptionMessage(Throwable e) {
    // don't send sensitive DB information back to the user.
    if (e instanceof DataAccessException) {
      log.error("{} db exception: {}", e.getClass(), e.getMessage());
      return "Database exception - query could not be executed.";
      // this is just to prevent bad-looking 'null' messages passed to
      // user
    } else if (e instanceof NullPointerException) {
      return "An expected resource was unavailable (null). This is probably a server error - please"
          + " report this to support.";
    } else if (e instanceof ConstraintViolationException) {
      return getConstraintViolationMessage(((ConstraintViolationException) e));
    } else {
      StringBuffer message = new StringBuffer();
      while (e != null) {
        message.append(e.getMessage() + "\n\n");
        e = e.getCause();
        if (message.length() > exceptionMessageStringLength) {
          break;
        }
      }
      return message.toString();
    }
  }

  private String getConstraintViolationMessage(ConstraintViolationException e) {
    return e.getConstraintViolations().stream()
        .map(cv -> cv.getMessage())
        .collect(Collectors.joining());
  }
}
