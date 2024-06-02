package com.researchspace.webapp.integrations.argos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.argos.model.ArgosDMP;
import com.researchspace.argos.model.ArgosDMPListing;
import com.researchspace.argos.model.DataTableData;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.impl.ConditionalTestRunnerNotSpring;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.web.client.RestTemplate;

@RunWith(ConditionalTestRunnerNotSpring.class)
public class ArgosApiRealConnectionTest {

  private RestTemplate restTemplate;

  @Mock private PropertyHolder propertyHolder;

  @InjectMocks private ArgosDMPProvider argosClient;

  @Before
  public void setUp() throws Exception {
    openMocks(this);

    restTemplate = new RestTemplate();

    this.argosClient = new ArgosDMPProvider(new URL("https://devel.opendmp.eu/srv/api/public"));
    this.argosClient.setRestTemplate(restTemplate);
  }

  // The following tests run nightly and assert that the Argos API has not
  // changed in such a way that our integration no longer works correctly
  @RunIfSystemPropertyDefined("nightly")
  @Test
  public void listPlansTest() {
    try {
      DataTableData<ArgosDMPListing> list = argosClient.listPlans(10, 0, null, null, null, null);
      assertTrue(list.getData().size() >= 0);
    } catch (MalformedURLException | URISyntaxException e) {
      fail("argosClient.listPlans threw an exception.");
    }
  }

  @RunIfSystemPropertyDefined("nightly")
  @Test
  public void getPlanByIdTest() {
    try {
      DataTableData<ArgosDMPListing> list = argosClient.listPlans(1, 0, null, null, null, null);
      String id = list.getData().get(0).id;
      ArgosDMP plan = argosClient.getPlanById(id);
      assertEquals(plan.id, id);
    } catch (MalformedURLException | URISyntaxException e) {
      fail("argosClient.getPlanById threw an exception." + e.getMessage());
    }
  }
}
