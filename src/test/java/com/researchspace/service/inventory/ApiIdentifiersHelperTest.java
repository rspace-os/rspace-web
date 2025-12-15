package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.properties.IPropertyHolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
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
}
