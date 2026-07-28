package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.properties.IPropertyHolder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Fast unit tests for the barcode image enclosure link. The barcode data must travel as a genuine
 * query string: an earlier implementation pushed "?content=..." through the URI builder's path,
 * which encoded the "?" into the path and double-encoded the data. Such links never resolved to an
 * image, and Jetty 12 rejects them outright as ambiguous URIs.
 */
public class InventoryBarcodeEnclosureLinkTest {

  private static final String SERVER_URL = "https://rspace.example.com";

  private final BaseApiInventoryController controller = new BaseApiInventoryController();

  @BeforeEach
  public void setUp() {
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn(SERVER_URL);
    ReflectionTestUtils.setField(controller, "properties", properties);
  }

  private String enclosureLinkFor(String barcodeData) {
    ApiBarcode barcode = new ApiBarcode(barcodeData);
    ApiSampleInfo sample = new ApiSampleInfo();
    sample.setBarcodes(List.of(barcode));
    controller.addFileAndBarcodeLinks(sample);
    return barcode
        .getLinkOfType(ApiLinkItem.ENCLOSURE_REL)
        .orElseThrow(() -> new AssertionError("no enclosure link was added"))
        .getLink();
  }

  @Test
  public void enclosureLinkIsAGenuineQueryStringWithSingleEncodedContent() {
    String link = enclosureLinkFor("https://www.wikipedia.org/");

    assertEquals(
        SERVER_URL
            + "/api/inventory/v1/barcodes?content=https%3A%2F%2Fwww.wikipedia.org%2F&barcodeType=QR",
        link);
    assertFalse(link.contains("%3F"), "the '?' must not be encoded into the path: " + link);
    assertFalse(link.contains("%25"), "the barcode data must not be double-encoded: " + link);
  }

  @Test
  public void barcodeDataRoundTripsThroughTheLinkQueryParameter() {
    String data = "https://www.wikipedia.org/";
    UriComponents parsed = UriComponentsBuilder.fromUriString(enclosureLinkFor(data)).build();

    assertEquals("/api/inventory/v1/barcodes", parsed.getPath());
    assertEquals(
        data, UriUtils.decode(parsed.getQueryParams().getFirst("content"), StandardCharsets.UTF_8));
    assertEquals("QR", parsed.getQueryParams().getFirst("barcodeType"));
  }

  @Test
  public void reservedCharactersInBarcodeDataCannotInjectExtraQueryParameters() {
    String data = "MIX&barcodeType=EAN13 x";
    UriComponents parsed = UriComponentsBuilder.fromUriString(enclosureLinkFor(data)).build();

    assertEquals(List.of("QR"), parsed.getQueryParams().get("barcodeType"));
    assertEquals(
        data, UriUtils.decode(parsed.getQueryParams().getFirst("content"), StandardCharsets.UTF_8));
  }

  @Test
  public void blankBarcodeDataGetsNoEnclosureLink() {
    ApiBarcode barcode = new ApiBarcode(" ");
    ApiSampleInfo sample = new ApiSampleInfo();
    sample.setBarcodes(List.of(barcode));
    controller.addFileAndBarcodeLinks(sample);

    assertTrue(barcode.getLinkOfType(ApiLinkItem.ENCLOSURE_REL).isEmpty());
  }
}
