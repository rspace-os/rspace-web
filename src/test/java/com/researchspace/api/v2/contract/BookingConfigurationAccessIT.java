package com.researchspace.api.v2.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.booking.service.BookingResourceRoleScheme;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import com.researchspace.webapp.controller.MVCTestBase;
import java.util.ArrayList;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ApiV2WebIntegrationTest
class BookingConfigurationAccessIT extends MVCTestBase {
  private ApiV2Fixture fixture;
  private final PlatformTransactionManager transactions;

  @Autowired
  BookingConfigurationAccessIT(PlatformTransactionManager transactions) {
    this.transactions = transactions;
  }

  @AfterEach
  void cleanFixture() {
    if (fixture != null) fixture.cleanUp();
  }

  @Test
  void unreadableConfigurationIsHiddenFromSingleAndBulkWrites() throws Exception {
    fixture = ApiV2Fixture.in(wac);
    var mvc = fixture.mockMvc();
    long instrument = fixture.instrument(fixture.user(), fixture.marker());
    long configuration = fixture.bookingConfiguration(instrument, "UTC", fixture.userKey());
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            ignored -> {
              var session = wac.getBean(SessionFactory.class).getCurrentSession();
              var row = session.get(BookingConfiguration.class, configuration);
              var access = row.getResourceAccess();
              new ArrayList<>(access.getAssignments())
                  .stream()
                      .filter(
                          assignment ->
                              !assignment.getRoleKey().equals(BookingResourceRoleScheme.OWNER))
                      .forEach(access::removeAssignment);
              session.flush();
            });
    String path = "/api/v2/booking-configurations/" + configuration;
    mvc.perform(
            patch(path)
                .header("apiKey", fixture.otherUserKey())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isNotFound());
    mvc.perform(delete(path).header("apiKey", fixture.otherUserKey()).header("If-Match", "\"0\""))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/v2/booking-configurations")
                .queryParam("where", "id=gt=0")
                .header("apiKey", fixture.otherUserKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isForbidden());
    mvc.perform(get(path).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.configurationVersion").value(0));
  }
}
