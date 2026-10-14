package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.controller.ApiControllerAdvice;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.core.util.BasicPaginationCriteria;
import com.researchspace.core.util.DateRange;
import com.researchspace.core.util.imageutils.BarcodeUtils;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.model.field.LocalizedIOException;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.field.LocalizedNullPointerException;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ImportedUtilityMessagesTest {

  private final TestApiControllerAdvice advice = new TestApiControllerAdvice();

  @Test
  void malformedImageAndEmptyBarcodeMessagesAreResolvedInApiResponses() {
    LocalizedIllegalArgumentException malformedImage =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () -> ImageUtils.getImageBytesFromBase64DataImage("data:image/png;base64,»Þõûã"));
    ResponseEntity<Object> imageResponse =
        advice.handleLocalizedIllegalArgumentException(malformedImage, null);

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, imageResponse.getStatusCode());
    ApiError imageError = (ApiError) imageResponse.getBody();
    assertEquals("Image string doesn't seem to be base64-encoded", imageError.getMessage());
    assertEquals(ApiErrorCodes.ILLEGAL_ARGUMENT.getCode(), imageError.getInternalCode());

    LocalizedIllegalArgumentException emptyBarcode =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () -> BarcodeUtils.generateBarcodeImage("", null, null));
    ResponseEntity<Object> barcodeResponse =
        advice.handleLocalizedIllegalArgumentException(emptyBarcode, null);

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, barcodeResponse.getStatusCode());
    ApiError barcodeError = (ApiError) barcodeResponse.getBody();
    assertEquals("Barcode text cannot be empty", barcodeError.getMessage());
    assertEquals(ApiErrorCodes.ILLEGAL_ARGUMENT.getCode(), barcodeError.getInternalCode());
  }

  @Test
  void nullImageAndBarcodeArgumentsRemainNullPointerExceptions() {
    NullPointerException nullImage =
        assertThrows(
            NullPointerException.class, () -> ImageUtils.getExtensionFromBase64DataImage(null));
    assertInstanceOf(LocalizedNullPointerException.class, nullImage);
    ResponseEntity<Object> imageResponse =
        advice.handleLocalizedIllegalStateException(nullImage, null);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, imageResponse.getStatusCode());
    ApiError imageError = (ApiError) imageResponse.getBody();
    assertEquals(
        "Expected base64 web image string but was null or empty.", imageError.getMessage());
    assertEquals(ApiErrorCodes.GENERAL_ERROR.getCode(), imageError.getInternalCode());

    NullPointerException nullBarcode =
        assertThrows(
            NullPointerException.class, () -> BarcodeUtils.generateBarcodeImage(null, null, null));
    assertInstanceOf(LocalizedNullPointerException.class, nullBarcode);
  }

  @Test
  void localizedIOExceptionUsesTheInternalServerErrorHandler() {
    LocalizedIOException exception =
        new LocalizedIOException(
            "validation.imageConversion.conversionFailed", new File("diagram.tiff"));

    ResponseEntity<Object> response = advice.handleLocalizedIllegalStateException(exception, null);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    ApiError error = (ApiError) response.getBody();
    assertEquals("Could not convert diagram.tiff to  a png format", error.getMessage());
    assertEquals(ApiErrorCodes.GENERAL_ERROR.getCode(), error.getInternalCode());
  }

  @Test
  void dateRangeAndPaginationMessagesResolveArguments() {
    IllegalArgumentException invalidDateRange =
        assertThrows(IllegalArgumentException.class, () -> DateRange.parse(",1900/01/23"));
    assertEquals(
        "could not parse [,1900/01/23]. Argument must match one or both of "
            + "^\\d{4}\\-\\d{2}\\-\\d{2}, or ,\\s*(\\d{4}\\-\\d{2}\\-\\d{2})$",
        advice.resolve(invalidDateRange));

    BasicPaginationCriteria<Object> criteria = new BasicPaginationCriteria<>(Object.class);
    IllegalArgumentException negativePage =
        assertThrows(IllegalArgumentException.class, () -> criteria.setPageNumber(-1L));
    assertEquals("Page number cannot be -ve, was [-1]", advice.resolve(negativePage));

    IllegalArgumentException missingSortOrder =
        assertThrows(IllegalArgumentException.class, () -> criteria.setSortOrder(null));
    assertEquals("sort order cannot be null!", advice.resolve(missingSortOrder));
  }

  private static class TestApiControllerAdvice extends ApiControllerAdvice {
    TestApiControllerAdvice() {
      messages = new MessageSourceUtils(new JsonMessageSource());
    }

    String resolve(Throwable exception) {
      return messages.getExceptionMessage(exception);
    }
  }
}
