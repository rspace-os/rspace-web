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

  /**
   * Allows clients to override the 5 main types of exception handling logging (RSpace General,Security,Constraint,Public Link, Spring Web Client) and two main
   * types of Model and View to return: 'Ajax' and 'non Ajax'. Call the overloaded method
   * handleExceptions with this as a fourth parameter. Classes that implement this interface should return true
   * for each part of the exception handling logging they perform themselves. For example, if an implementation of this interface
   * handles Ajax errors but not Spring Web Client of RSpace errors it would return false for handleRSpaceExceptions, false for
   * handleSpringWebClientExceptions and true for handleAjaxExceptions. Classes that implement this interface should return a ModelAndView
   * for each part of the ExceptionHandling return value generation they implement themselves and null if they do not implement a return value
   * for exception handling. For example, to return the default ModelAndView for Ajax requests, an implementation of this interface should return null for visitorHandleAjaxExceptionReturnValue.
   * To return an customised ModelAndView for non ajax requests, an implementation of this interface should return a non null ModelAndView.
   */
  public static interface ExceptionHandlerVisitor {

    boolean visitorHasHandledGeneralRSpaceExceptionLogging(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId);

    boolean visitorHasHandledPublicLinkExceptionLogging(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId);

    boolean visitorHasHandleSecurityExceptionLogging(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId);

    boolean visitorHasHandleConstraintExceptionLogging(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId);

    boolean visitorHasHandleSpringWebClientExceptionLogging(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId);

    ModelAndView visitorHandleAjaxExceptionReturnValue(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId);

    ModelAndView visitorHandleNonAjaxExceptionReturnValue(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId);

    default void logErrorFromException(Exception e, String errorId) {
      ControllerExceptionHandler.logErrorFromException(e, errorId);
    }

    default void logWarningFromException(Exception e, String errorId) {
      ControllerExceptionHandler.logWarningFromException(e, errorId);
    }

    default void addTimeStampAndErrorId(String tstamp, String errorId, ModelAndView m) {
      ControllerExceptionHandler.addTimeStampAndErrorId(tstamp, errorId, m);
    }

    default void setErrorResponseStatus(HttpServletResponse response, Exception e) {
      ControllerExceptionHandler.setErrorResponseStatus(response, e);
    }
  }
  private static final ExceptionHandlerVisitor DEFAULT_VISITOR = new DefaultExceptionHandlerVisitor();
  /**
   * The default visitor signals that it does not implement any exception handling - therefore code
   * in the outer class, ControllerExceptionHandler, will be completely unaffected by its use. Child classes of
   * DefaultExceptionHandlerVisitor can override the methods they wish to modify and then call the overloaded
   * 4 param method handleExceptions, passing in the child of DefaultExceptionHandlerVisitor as the 4th parameter
   */
  public static class DefaultExceptionHandlerVisitor implements ExceptionHandlerVisitor {

    @Override
    public boolean visitorHasHandledGeneralRSpaceExceptionLogging(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId) {
      return false;
    }

    @Override
    public boolean visitorHasHandledPublicLinkExceptionLogging(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId) {
      return false;
    }

    @Override
    public boolean visitorHasHandleSecurityExceptionLogging(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId) {
      return false;
    }

    @Override
    public boolean visitorHasHandleConstraintExceptionLogging(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId) {
      return false;
    }

    @Override
    public boolean visitorHasHandleSpringWebClientExceptionLogging(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId) {
      return false;
    }

    @Override
    public ModelAndView visitorHandleAjaxExceptionReturnValue(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId) {
      return null;
    }

    @Override
    public ModelAndView visitorHandleNonAjaxExceptionReturnValue(HttpServletRequest request,
        HttpServletResponse response, Exception e, String timestamp, String errorId) {
      return null;
    }

  };

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
   * requests. Logs security exceptions to Security log.
   * Child classes of BaseController that need to implement custom error handling
   * should override the 'handleExceptions' method of BaseController and call the overloaded 4 parameter 'handleExceptions' method of this class
   * with an implementation of ExceptionHandlerVisitor as the 4th parameter
   */
  public ModelAndView handleExceptions(
      HttpServletRequest request, HttpServletResponse response, Exception e) {
      return handleExceptions(request, response, e, DEFAULT_VISITOR);
  }
  /**
   * Logs exceptions, stack traces and returns a suitable view for both regular and Ajax based
   * requests. Logs security exceptions to Security log. See ControllerExceptionHandler.ExceptionHandlerVisitor for
   * an explanation of how this may be used to modify exception handling in this class
   */
  public ModelAndView handleExceptions(
      HttpServletRequest request, HttpServletResponse response, Exception e, ExceptionHandlerVisitor visitor) {
    // handle authentication/authorisation exceptions differently.
    String tstamp = DateUtil.convertDateToISOFormat(new Date(), TimeZone.getDefault());
    String errorId = LoggingUtils.generateLogId();
    if (isSecurityException(e)) {
      if(!visitor.visitorHasHandledGeneralRSpaceExceptionLogging(request, response, e, tstamp, errorId)) {
        log.warn("Handling security-related exception {}: {}", errorId, e.getMessage());
        SECURITY_LOG.warn(
            "Security-related exception [{}] by user [{}] to [{}]: {}",
            errorId,
            SecurityUtils.getSubject().getPrincipal(),
            request.getRequestURI(),
            e.getMessage());
      }
    }
     else if (e instanceof PublicLinkNotFoundException) {
       if(!visitor.visitorHasHandledPublicLinkExceptionLogging(request, response, e, tstamp, errorId)) {
         log.warn(
             "The following external public link was not found in this instance of RSpace: "
                 + e.getMessage());
         return new ModelAndView("/public/publicLinkNotFound");
       }
    } else {
      if (e instanceof HttpClientErrorException) {
        if(!visitor.visitorHasHandleSpringWebClientExceptionLogging(request, response, e, tstamp, errorId)) {
          if (((HttpClientErrorException) e).getStatusCode().is5xxServerError()) {
            logErrorFromException(e, errorId);
          } else {
            logWarningFromException(e, errorId);
          }
        }
      } else if (e instanceof ConstraintViolationException) {
        if(!visitor.visitorHasHandleConstraintExceptionLogging(request, response, e, tstamp, errorId)) {
          logWarningFor(getConstraintViolationMessage((ConstraintViolationException) e), errorId);
        }
      } else {
        if(!visitor.visitorHasHandledGeneralRSpaceExceptionLogging(request, response, e, tstamp, errorId)) {
          logErrorFromException(e, errorId);
        }
      }
    }

    String ajaxErrorView = AJAX_ERROR_VIEW_NAME;
    String ajaxDefaultErrorMessage = AJAX_DEFAULT_ERROR_MSG;
    boolean ajaxShowTechMessage = true;
    ModelAndView visitedValue = null;
    if (isAjax(request)) {
      visitedValue = visitor.visitorHandleAjaxExceptionReturnValue(request, response, e, tstamp, errorId);
      if (visitedValue == null) {
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
        return visitedValue;
      }
    } else {
      visitedValue = visitor.visitorHandleNonAjaxExceptionReturnValue(request, response, e, tstamp, errorId);
      if (visitedValue == null) {
        ModelAndView m = new ModelAndView(NON_AJAX_ERROR_VIEW_NAME);
        m.addObject(EXCEPTION_MESSAGE_ATTR_NAME, getExceptionMessage(e));
        addTimeStampAndErrorId(tstamp, errorId, m);
        return m;
      }
      else {
        return visitedValue;
      }
    }
  }

  private static void logErrorFromException(Exception e, String errorId) {
    log.error(
        "Unexpected exception in controller:errorId-[{}]. [{}]. {}",
        errorId,
        e.getMessage(),
        convertStackTraceToString(e));
  }

  private static void logWarningFromException(Exception e, String errorId) {
    log.warn(
        "Expected exception type in controller:errorId-[{}]. [{}]. {}",
        errorId,
        e.getMessage(),
        convertStackTraceToString(e));
  }

  private void logWarningFor(String message, String errorId) {
    log.warn("Expected exception type in controller:errorId-[{}]. [{}]. {}", errorId, message);
  }

  private static void addTimeStampAndErrorId(String tstamp, String errorId, ModelAndView m) {
    m.addObject("tstamp", tstamp);
    m.addObject("errorId", errorId);
  }

  private static void setErrorResponseStatus(HttpServletResponse response, Exception e) {
    if (isSecurityException(e)) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    } else {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private static boolean isSecurityException(Exception e) {
    return e instanceof RecordAccessDeniedException
        || e instanceof AuthorizationException
        || e instanceof AuthenticationException;
  }

  private static String convertStackTraceToString(Exception e) {
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
