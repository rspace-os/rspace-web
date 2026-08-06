package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.icu.text.ListFormatter;
import com.researchspace.api.v2.model.ApiV2PaginationCriteria;
import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.DocumentValidationException.Reason;
import com.researchspace.model.collection.DocumentValidationException.Violation;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.ListFormatUtils;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import java.util.Locale;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiV2ControllerAdviceTest {

  private ApiV2ControllerAdvice advice;

  @BeforeEach
  void setUp() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage("errors.api.v2.forbidden", Locale.getDefault(), "Forbidden detail");
    source.addMessage(
        "errors.api.pagination.page.min", Locale.getDefault(), "Page must be 1 or greater.");
    source.addMessage(
        "errors.api.v2.authenticationRequired", Locale.getDefault(), "Authentication detail");
    source.addMessage("errors.api.v2.invalidRequest", Locale.getDefault(), "Invalid detail");
    source.addMessage("errors.api.v2.notFound", Locale.getDefault(), "Not found detail");
    source.addMessage("errors.api.v2.query.field", Locale.getDefault(), "Invalid field detail");
    source.addMessage("errors.api.v2.select.mode", Locale.getDefault(), "Invalid select detail");
    source.addMessage(
        "errors.api.v2.resource.conflict", Locale.getDefault(), "Resource conflict: {0}");
    source.addMessage("errors.api.v2.bulk.limit", Locale.getDefault(), "Bulk limit detail");
    source.addMessage("errors.api.v2.tooManyRequests", Locale.getDefault(), "Throttle detail");
    source.addMessage("errors.api.v2.unexpected", Locale.getDefault(), "Unexpected detail");
    source.addMessage(
        "errors.api.v2.methodNotAllowed", Locale.getDefault(), "Method not allowed detail");
    source.addMessage("errors.api.v2.notAcceptable", Locale.getDefault(), "Not acceptable detail");
    source.addMessage(
        "errors.api.v2.unsupportedMediaType", Locale.getDefault(), "Unsupported media detail");
    source.addMessage(
        "errors.api.v2.missingParameter", Locale.getDefault(), "Missing parameter detail");
    source.addMessage("errors.api.v2.requestRejected", Locale.getDefault(), "Rejected detail");
    advice = new ApiV2ControllerAdvice(new MessageSourceUtils(source));
  }

  @Test
  void mapsAuthenticationValidationAndTypeErrorsToProblemDetails() {
    ResponseEntity<ApiV2Problem> authentication = advice.handleAuthentication();
    assertProblem(
        authentication,
        HttpStatus.UNAUTHORIZED,
        "errors.api.v2.authenticationRequired",
        "Authentication detail");
    assertEquals("Authentication detail", authentication.getBody().title());
    assertEquals("Bearer", authentication.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
    assertProblem(
        advice.handleTypeMismatch(),
        HttpStatus.BAD_REQUEST,
        "errors.api.v2.invalidRequest",
        "Invalid detail");
    assertProblem(
        advice.handleConstraintViolation(),
        HttpStatus.BAD_REQUEST,
        "errors.api.v2.invalidRequest",
        "Invalid detail");
  }

  @Test
  void localizesFieldAndGlobalValidationErrors() {
    ApiV2PaginationCriteria pagination = new ApiV2PaginationCriteria();
    pagination.setPage(0);
    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(pagination, "pagination");
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(new JsonMessageSource());
    validator.afterPropertiesSet();
    validator.validate(pagination, errors);
    validator.close();
    errors.reject("errors.api.v2.invalidRequest");

    ResponseEntity<ApiV2Problem> response = advice.handleBindException(new BindException(errors));

    assertProblem(
        response,
        HttpStatus.BAD_REQUEST,
        "errors.api.v2.invalidRequest",
        ListFormatUtils.formatList(
            List.of("Page must be 1 or greater.", "Invalid detail"),
            Locale.getDefault(),
            ListFormatter.Type.UNITS));
    assertEquals("Invalid detail", response.getBody().title());
  }

  @Test
  void mapsAuthorizationThrottlingAndUnexpectedErrorsToProblemDetails() {
    MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v2/maintenances/1");
    assertProblem(
        advice.handleAuthorization(new AuthorizationException("denied"), request),
        HttpStatus.FORBIDDEN,
        "errors.api.v2.forbidden",
        "Forbidden detail");
    assertProblem(
        advice.handleThrottling(),
        HttpStatus.TOO_MANY_REQUESTS,
        "errors.api.v2.tooManyRequests",
        "Throttle detail");
    assertProblem(
        advice.handleUnexpected(new RuntimeException("sensitive internal detail")),
        HttpStatus.INTERNAL_SERVER_ERROR,
        "errors.api.v2.unexpected",
        "Unexpected detail");
  }

  @Test
  void mapsNotFoundAndCollectionQueryErrorsToProblemDetails() {
    assertProblem(
        advice.handleNotFound(),
        HttpStatus.NOT_FOUND,
        "errors.api.v2.notFound",
        "Not found detail");
    assertProblem(
        advice.handleBadRequest(new ApiV2BadRequestException("errors.api.v2.select.mode")),
        HttpStatus.BAD_REQUEST,
        "errors.api.v2.select.mode",
        "Invalid select detail");
    assertProblem(
        advice.handleCollectionQuery(
            new CollectionQueryException(CollectionQueryException.Reason.FIELD)),
        HttpStatus.BAD_REQUEST,
        "errors.api.v2.query.field",
        "Invalid field detail");
    assertProblem(
        advice.handleCollectionMutation(
            new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT)),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "errors.api.v2.bulk.limit",
        "Bulk limit detail");
  }

  @Test
  void mapsResourceSpecErrorsToProblemDetails() {
    RuntimeException cause = new IllegalStateException("internal");
    var exception =
        ApiV2ErrorMapping.of(
                IllegalStateException.class,
                HttpStatus.CONFLICT,
                "errors.api.v2.resource.conflict",
                "The resource conflicts.",
                ignored -> new Object[] {"some-resource"})
            .translate(cause);

    assertProblem(
        advice.handleResourceException(exception),
        HttpStatus.CONFLICT,
        "errors.api.v2.resource.conflict",
        "Resource conflict: some-resource");
  }

  @Test
  void reportsAllInvalidDocumentFieldsWithoutExposingRejectedValues() {
    ResponseEntity<ApiV2Problem> response =
        advice.handleDocumentValidation(
            new DocumentValidationException(
                "errors.api.v2.invalidRequest",
                List.of(
                    new Violation("startDate", Reason.WRONG_TYPE),
                    new Violation("endDate", Reason.REQUIRED))));

    assertProblem(
        response, HttpStatus.BAD_REQUEST, "errors.api.v2.invalidRequest", "Invalid detail");
    assertEquals(
        List.of(
            new ApiV2Problem.InvalidParam("startDate", "wrongType"),
            new ApiV2Problem.InvalidParam("endDate", "required")),
        response.getBody().invalidParams());
  }

  @Test
  void mapsMethodMediaTypeAndMissingParameterErrorsToProblemDetails() {
    ResponseEntity<ApiV2Problem> methodNotAllowed =
        advice.handleUnexpected(
            new HttpRequestMethodNotSupportedException("POST", List.of("GET", "DELETE")));
    assertProblem(
        methodNotAllowed,
        HttpStatus.METHOD_NOT_ALLOWED,
        "errors.api.v2.methodNotAllowed",
        "Method not allowed detail");
    assertEquals(
        List.of("GET", "DELETE"),
        List.of(methodNotAllowed.getHeaders().getFirst(HttpHeaders.ALLOW).split("\\s*,\\s*")));
    assertProblem(
        advice.handleNotAcceptable(),
        HttpStatus.NOT_ACCEPTABLE,
        "errors.api.v2.notAcceptable",
        "Not acceptable detail");
    assertProblem(
        advice.handleUnsupportedMediaType(),
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "errors.api.v2.unsupportedMediaType",
        "Unsupported media detail");
    assertProblem(
        advice.handleMissingParameter(),
        HttpStatus.BAD_REQUEST,
        "errors.api.v2.missingParameter",
        "Missing parameter detail");
  }

  @Test
  void mapsResponseStatusExceptionsIncludingNonStandardCodes() {
    ResponseEntity<ApiV2Problem> response =
        advice.handleResponseStatus(
            new ResponseStatusException(HttpStatus.CONFLICT, "conflict reason"));
    assertProblem(
        response, HttpStatus.CONFLICT, "errors.api.v2.requestRejected", "Rejected detail");
    assertEquals("Rejected detail", response.getBody().title());

    ResponseEntity<ApiV2Problem> nonStandard =
        advice.handleResponseStatus(
            new ResponseStatusException(HttpStatusCode.valueOf(499), "client closed request"));
    assertProblem(
        nonStandard,
        HttpStatus.INTERNAL_SERVER_ERROR,
        "errors.api.v2.unexpected",
        "Unexpected detail");
  }

  @Test
  void keepsTheStatusOfSpringMvcExceptionsThatHaveNoDedicatedHandler() {
    assertProblem(
        advice.handleUnexpected(new NoResourceFoundException(HttpMethod.GET, "/api/v2/missing")),
        HttpStatus.NOT_FOUND,
        "errors.api.v2.notFound",
        "Not found detail");
    assertProblem(
        advice.handleUnexpected(
            new NoHandlerFoundException("GET", "/api/v2/missing", new HttpHeaders())),
        HttpStatus.NOT_FOUND,
        "errors.api.v2.notFound",
        "Not found detail");
    assertProblem(
        advice.handleUnexpected(new AsyncRequestTimeoutException()),
        HttpStatus.SERVICE_UNAVAILABLE,
        "errors.api.v2.unexpected",
        "Unexpected detail");
    assertProblem(
        advice.handleUnexpected(
            new ErrorResponseException(HttpStatus.BAD_GATEWAY, new RuntimeException("upstream"))),
        HttpStatus.BAD_GATEWAY,
        "errors.api.v2.unexpected",
        "Unexpected detail");
  }

  @Test
  void stillReportsAnExceptionWithNoDeclaredStatusAsInternalServerError() {
    ResponseEntity<ApiV2Problem> response =
        advice.handleUnexpected(new IllegalStateException("sensitive internal detail"));

    assertProblem(
        response,
        HttpStatus.INTERNAL_SERVER_ERROR,
        "errors.api.v2.unexpected",
        "Unexpected detail");
    assertFalse(response.getBody().detail().contains("sensitive internal detail"));
  }

  @Test
  void appliesOnlyToV2Controllers() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton("apiV2ControllerAdvice", advice);
    ControllerAdviceBean adviceBean =
        new ControllerAdviceBean(
            "apiV2ControllerAdvice",
            beanFactory,
            ApiV2ControllerAdvice.class.getAnnotation(ControllerAdvice.class));

    assertTrue(adviceBean.isApplicableToBeanType(UsersV2Controller.class));
    assertEquals(
        Ordered.HIGHEST_PRECEDENCE, ApiV2ControllerAdvice.class.getAnnotation(Order.class).value());
  }

  private static void assertProblem(
      ResponseEntity<ApiV2Problem> response, HttpStatus status, String code, String detail) {
    assertEquals(status, response.getStatusCode());
    assertEquals(ApiV2Problem.PROBLEM_JSON, response.getHeaders().getContentType());
    assertEquals(code, response.getBody().code());
    assertEquals(detail, response.getBody().detail());
  }
}
