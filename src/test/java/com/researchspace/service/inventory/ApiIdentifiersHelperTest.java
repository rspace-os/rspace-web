package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.properties.IPropertyHolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ApiIdentifiersHelperTest {

  @Mock private IPropertyHolder properties;
  @Mock private User user;
  @InjectMocks private ApiIdentifiersHelper underTest;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    when(properties.getServerUrl()).thenReturn("https://localhost:8080");
  }

  @Test
  public void createDoiToSave() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    DataCiteDoi doiRegistered =
        mapper.readValue(
            IOUtils.resourceToString(
                "/TestResources/datacite/dataCiteDoi.json", StandardCharsets.UTF_8),
            DataCiteDoi.class);
    ApiInventoryDOI apiDoi = new ApiInventoryDOI(user, doiRegistered);

    DigitalObjectIdentifier result = underTest.createDoiToSave(apiDoi, user);
    assertNotNull(result);
    assertTrue(
        result
            .getOtherData(DigitalObjectIdentifier.IdentifierOtherProperty.LOCAL_URL)
            .contains("https://localhost:8080/public/inventory/"));
    assertEquals(user, result.getOwner());
  }

  @Test
  public void createDoiToSavePersistsPidinstType() {
    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.setDoiType(IdentifierType.PIDINST_DATACITE.name());

    DigitalObjectIdentifier result = underTest.createDoiToSave(apiDoi, user);
    assertEquals(IdentifierType.PIDINST_DATACITE, result.getType());
  }

  @Test
  public void createDoiToSaveDefaultsToIgsnTypeWhenNoDoiType() {
    ApiInventoryDOI apiDoi = new ApiInventoryDOI();

    DigitalObjectIdentifier result = underTest.createDoiToSave(apiDoi, user);
    assertEquals(IdentifierType.IGSN_DATACITE, result.getType());
  }

  @Test
  public void createDoiToSaveIgnoresNonEnumDoiType() {
    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    // "dois" is the JSON:API type literal copied from DataCite responses, not an IdentifierType
    apiDoi.setDoiType("dois");

    DigitalObjectIdentifier result = underTest.createDoiToSave(apiDoi, user);
    assertEquals(IdentifierType.IGSN_DATACITE, result.getType());
  }

  /**
   * The invariant RSDEV-1254 exists for: the suffix registered with the provider (carried on the
   * DTO) is the one the entity's publicLink adopts, so the registered address and the page RSpace
   * serves can never diverge. See ADR 0006.
   */
  @Test
  public void createDoiToSaveUsesDtoSuffixAsEntityPublicLink() {
    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.generatePublicLinkSuffix();

    DigitalObjectIdentifier result = underTest.createDoiToSave(apiDoi, user);

    assertEquals(apiDoi.getPublicLinkSuffix(), result.getPublicLink());
    assertEquals(
        "https://localhost:8080/public/inventory/" + apiDoi.getPublicLinkSuffix(),
        result.getOtherData(DigitalObjectIdentifier.IdentifierOtherProperty.LOCAL_URL));
  }

  @Test
  public void createDoiToSaveStillGeneratesPublicLinkWhenDtoCarriesNoSuffix() {
    ApiInventoryDOI apiDoi = new ApiInventoryDOI();

    DigitalObjectIdentifier result = underTest.createDoiToSave(apiDoi, user);

    assertNotNull(result.getPublicLink()); // entity fallback keeps suffix-less callers safe
    // and LOCAL_URL must be built from *that* value: a regression to the DTO's (absent) suffix
    // would leave the property off every bulk IGSN allocation, which this test would otherwise
    // still pass.
    assertEquals(
        "https://localhost:8080/public/inventory/" + result.getPublicLink(),
        result.getOtherData(DigitalObjectIdentifier.IdentifierOtherProperty.LOCAL_URL));
  }

  /**
   * Same rule as {@code InventoryUrls.publicLandingPageUrl}, which the registered address also
   * server URL configured the address is omitted rather than persisted as the literal
   * "null/public/inventory/...", which would surface as the identifier's url over the API.
   */
  @Test
  public void createDoiToSaveOmitsLocalUrlWhenNoServerUrlIsConfigured() {
    when(properties.getServerUrl()).thenReturn(null);
    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.generatePublicLinkSuffix();

    DigitalObjectIdentifier result = underTest.createDoiToSave(apiDoi, user);

    assertNull(result.getOtherData(DigitalObjectIdentifier.IdentifierOtherProperty.LOCAL_URL));
  }

  @Test
  public void createDoiToSaveToleratesTrailingSlashOnServerUrl() {
    when(properties.getServerUrl()).thenReturn("https://localhost:8080/");
    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.generatePublicLinkSuffix();

    DigitalObjectIdentifier result = underTest.createDoiToSave(apiDoi, user);

    assertEquals(
        "https://localhost:8080/public/inventory/" + apiDoi.getPublicLinkSuffix(),
        result.getOtherData(DigitalObjectIdentifier.IdentifierOtherProperty.LOCAL_URL));
  }

  /**
   * The entity generates its own suffix when handed none, which is right for every path with no
   * address to preserve: bulk IGSN allocation, RecordFactory.createDoiIdentifier, imports. On the
   * register path it would be exactly wrong. By the time this runs the address has already gone to
   * the provider, so a freshly generated suffix would publish an address RSpace never serves,
   * permanently and irreversibly once a curator accepts the record.
   *
   * <p>So the register path refuses rather than leaning on the fallback. It cannot happen today
   * (both producers generate before setting the flag), which is the point: if it ever does, the
   * suffix was lost in between and silence would be the worst possible response.
   */
  @Test
  public void registerRequestWithoutASuffixFailsRatherThanGeneratingADifferentOne() {
    ApiInventoryDOI apiDoi = new ApiInventoryDOI(); // deliberately no generatePublicLinkSuffix()
    apiDoi.setRegisterIdentifierRequest(true);
    InventoryRecord parent = mock(InventoryRecord.class);

    assertThrows(
        IllegalStateException.class,
        () -> underTest.createDeleteRequestedIdentifiers(List.of(apiDoi), parent, user));
  }

  /**
   * The same invariant on the path RSDEV-1254 actually exists for. A registration that attaches the
   * identifier to a record arrives through {@code createDeleteRequestedIdentifiers} with the
   * register flag set, not through {@code createDoiToSave} (which is the bulk IGSN path), so
   * without this the entity-adopts-the-DTO-suffix rule was pinned only on the other flow. See ADR
   * 0006.
   */
  @Test
  public void registerRequestUsesDtoSuffixAsEntityPublicLink() {
    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.generatePublicLinkSuffix();
    apiDoi.setRegisterIdentifierRequest(true);
    InventoryRecord parent = mock(InventoryRecord.class);
    when(parent.getOwner()).thenReturn(user);

    assertTrue(underTest.createDeleteRequestedIdentifiers(List.of(apiDoi), parent, user));

    ArgumentCaptor<DigitalObjectIdentifier> attached =
        ArgumentCaptor.forClass(DigitalObjectIdentifier.class);
    verify(parent).addIdentifier(attached.capture());
    assertEquals(apiDoi.getPublicLinkSuffix(), attached.getValue().getPublicLink());
    assertEquals(
        "https://localhost:8080/public/inventory/" + apiDoi.getPublicLinkSuffix(),
        attached
            .getValue()
            .getOtherData(DigitalObjectIdentifier.IdentifierOtherProperty.LOCAL_URL));
  }
}
