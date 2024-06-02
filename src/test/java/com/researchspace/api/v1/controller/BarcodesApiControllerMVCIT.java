package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class BarcodesApiControllerMVCIT extends API_MVC_InventoryTestBase {

  User anyUser;
  String apiKey;

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createApiKeyForuser(anyUser);
  }

  @Test
  public void testRetrieveCommonBarcodes() throws Exception {
    // standard barcode
    MvcResult result;
    result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/barcodes", anyUser)
                    .param("content", "SA123"))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(98, result.getResponse().getContentAsByteArray().length); // expected barcode size

    // QR barcode
    result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/barcodes", anyUser)
                    .param("content", "SA123")
                    .param("barcodeType", "QR"))
            .andExpect(status().isOk())
            .andReturn();
    int actualLength = result.getResponse().getContentAsByteArray().length;
    // generated QR code size differs slightly between java 8 and 11
    assertTrue(actualLength > 280 && actualLength < 300, "uexpected length: " + actualLength);
  }

  @Test
  public void testBarcodeErrors() throws Exception {
    // empty content
    MvcResult result;
    result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/barcodes", anyUser))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertTrue(
        result.getResolvedException().getMessage().contains("Content parameter is required"));

    // unknown type requested
    result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/barcodes", anyUser)
                    .param("content", "SA12")
                    .param("barcodeType", "QRXD"))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertTrue(
        result
            .getResolvedException()
            .getMessage()
            .contains("Supported barcodeType values are: 'BARCODE' or 'QR'"));

    // incorrect width value
    result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/barcodes", anyUser)
                    .param("content", "SA12")
                    .param("barcodeType", "QR")
                    .param("imageWidth", "-1"))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertTrue(
        result
            .getResolvedException()
            .getMessage()
            .contains("Requested width cannot be less than zero"));

    // confirm correct request
    result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/barcodes", anyUser)
                    .param("content", "SA12")
                    .param("barcodeType", "QR")
                    .param("imageWidth", "15"))
            .andExpect(status().isOk())
            .andReturn();
  }
}
