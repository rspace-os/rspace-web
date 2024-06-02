package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.apiutils.ApiError;
import java.io.IOException;
import java.text.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.http.MockHttpOutputMessage;

public class CSVApiErrorMessageConverterTest {

  CSVApiErrorMessageConverter csvConverter;

  @Before
  public void setUp() throws Exception {
    csvConverter = new CSVApiErrorMessageConverter();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSupportsApiError() {
    assertTrue(csvConverter.supports(ApiError.class));
  }

  @Test
  public void testWriteInternalObjectHttpOutputMessage()
      throws HttpMessageNotWritableException, IOException, ParseException {
    ApiError apiError =
        new ApiError(HttpStatus.NOT_FOUND, 50301, "Document with id [2323] not found", "");
    MockHttpOutputMessage message = new MockHttpOutputMessage();
    csvConverter.writeInternal(apiError, message);
    String csvResponse = message.getBodyAsString();
    String[] lines = API_ModelTestUtils.parseCSVResponseToLines(csvResponse);

    assertEquals(2, lines.length); // 1 header row and 1 error row
    final int ERROR_PROPERTY_COUNT = 5;
    API_ModelTestUtils.assertRowAndColumnCountForApiError(csvResponse, 2, ERROR_PROPERTY_COUNT);
  }
}
