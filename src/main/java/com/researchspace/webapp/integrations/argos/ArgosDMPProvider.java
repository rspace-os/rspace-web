package com.researchspace.webapp.integrations.argos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.argos.client.ArgosClient;
import com.researchspace.argos.client.ArgosClientImpl;
import com.researchspace.argos.model.ArgosDMP;
import com.researchspace.argos.model.ArgosDMPListing;
import com.researchspace.argos.model.Criteria;
import com.researchspace.argos.model.DataTableData;
import com.researchspace.argos.model.TableRequest;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMP;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

@Slf4j
public class ArgosDMPProvider {
  private RestTemplate restTemplate;
  private ArgosClient argosClient;
  private URL baseUrl;

  public ArgosDMPProvider(URL baseUrl) {
    this.baseUrl = baseUrl;
    this.restTemplate = new RestTemplate();
    this.argosClient = new ArgosClientImpl(baseUrl);
  }

  public void setRestTemplate(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Autowired MediaManager mediaManager;
  @Autowired UserManager userManager;
  @Autowired DMPManager dmpManager;

  public ArgosDMP getPlanById(String id) throws MalformedURLException, URISyntaxException {
    return this.argosClient.getPlanById(id);
  }

  public DataTableData<ArgosDMPListing> listPlans(
      Integer pageSize,
      Integer page,
      String like,
      String grantsLike,
      String fundersLike,
      String collaboratorsLike)
      throws MalformedURLException, URISyntaxException {
    return this.argosClient.listPlans(
        new TableRequest(
            pageSize,
            page,
            new Criteria(
                like,
                grantsLike == null
                    ? Collections.emptyList()
                    : Collections.singletonList(grantsLike),
                fundersLike == null
                    ? Collections.emptyList()
                    : Collections.singletonList(fundersLike),
                collaboratorsLike == null
                    ? Collections.emptyList()
                    : Collections.singletonList(collaboratorsLike))));
  }

  public Boolean importDmp(String id)
      throws URISyntaxException, MalformedURLException, JsonProcessingException, IOException {
    User user = userManager.getAuthenticatedUserInSession();
    var dmpDetails = getPlanById(id);
    log.info("Importing DMP: " + id + ", " + dmpDetails.getLabel());

    ObjectMapper objectMapper = new ObjectMapper();
    String json = objectMapper.writeValueAsString(dmpDetails);
    InputStream is = new ByteArrayInputStream(json.getBytes());
    var file = mediaManager.saveNewDMP(dmpDetails.getLabel() + ".json", is, user, null);

    DMP dmp = new DMP(id, dmpDetails.getLabel());
    var dmpUser = dmpManager.findByDmpId(dmp.getDmpId(), user).orElse(new DMPUser(user, dmp));
    if (file != null) {
      dmpUser.setDmpDownloadPdf(file);
    } else {
      log.warn("Unexpected null DMP PDF - did download work?");
    }
    dmpManager.save(dmpUser);

    return Boolean.TRUE;
  }
}
