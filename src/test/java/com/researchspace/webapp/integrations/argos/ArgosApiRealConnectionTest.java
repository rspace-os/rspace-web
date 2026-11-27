package com.researchspace.webapp.integrations.argos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.dmps.argos.ArgosDMP;
import com.researchspace.model.dmps.argos.ArgosDMPListing;
import com.researchspace.model.dmps.argos.DataTableData;
import java.net.URL;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

// The following tests run nightly and assert that the Argos API has not
// changed in such a way that our integration no longer works correctly
@EnabledIfSystemProperty(named = "nightly", matches = "(|true)")
public class ArgosApiRealConnectionTest {

  private ArgosDMPProvider provider;

  @BeforeEach
  public void setUp() throws Exception {
    this.provider = new ArgosDMPProvider(new URL("https://argos.openaire.eu/api"));
  }

  @Test
  public void listPlansTest() {
    DataTableData<ArgosDMPListing> list = provider.listPlans(10, 0, null);
    assertThat(list.getData()).isNotEmpty();
    assertThat(list.getTotalCount()).isGreaterThanOrEqualTo(list.getData().size());
  }

  @Test
  public void getPlanByIdTest() {
    DataTableData<ArgosDMPListing> list = provider.listPlans(1, 0, null);
    String id = list.getData().get(0).id;
    ArgosDMP plan = provider.getPlanById(UUID.fromString(id));
    assertEquals(id, plan.id);
  }
}
