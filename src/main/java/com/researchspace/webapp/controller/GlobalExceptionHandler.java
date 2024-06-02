package com.researchspace.webapp.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;

/**
 * This can be used to handle exceptions that are thrown before reaching a controller, e.g. Spring
 * exceptions related to data conversions, file upload etc.
 *
 * <p>This will apply to all controllers.
 */
@ControllerAdvice()
public class GlobalExceptionHandler {

  void setHandler(IControllerExceptionHandler handler) {
    this.handler = handler;
  }

  @Autowired private IControllerExceptionHandler handler;

  Pattern fileSizes = Pattern.compile("(\\d+)");

  @ExceptionHandler(MultipartException.class)
  @ResponseStatus(code = HttpStatus.BAD_REQUEST)
  public ModelAndView handleMaxUploadException(
      MultipartException e, HttpServletRequest request, HttpServletResponse response) {
    // make a nicer display for end user
    if (e.getCause() != null) {
      String msg = e.getCause().getMessage();
      if (!StringUtils.isEmpty(msg)) {
        msg = convertBytesToDisplay(msg);
        IllegalArgumentException toobig = new IllegalArgumentException(msg, e);
        return handler.handleExceptions(request, response, toobig);
      }
    }
    return handler.handleExceptions(request, response, e);
  }

  String convertBytesToDisplay(String msg) {
    Matcher m = fileSizes.matcher(msg);
    while (m.find()) {
      String grp = m.group();
      String display = FileUtils.byteCountToDisplaySize(Long.parseLong(grp));
      msg = msg.replace(grp, display);
    }
    return msg;
  }
}
