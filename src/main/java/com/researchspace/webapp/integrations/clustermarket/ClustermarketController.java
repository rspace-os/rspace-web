package com.researchspace.webapp.integrations.clustermarket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.integrations.clustermarket.client.ClustermarketClient;
import com.researchspace.integrations.clustermarket.service.ClustermarketService;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/apps/clustermarket")
public class ClustermarketController {
  private final ClustermarketClient client;
  private final ClustermarketExceptionHandler clustermarketExceptionHandler;
  private final UserManager userManager;
  private final ClustermarketOAuthService clustermarketOAuthService;
  private final ClustermarketService clustermarketService;

  public ClustermarketController(
      ClustermarketClient client,
      UserManager userManager,
      ClustermarketService clustermarketService,
      ClustermarketOAuthService clustermarketOAuthService) {
    this.client = client;
    this.userManager = userManager;
    this.clustermarketExceptionHandler = new ClustermarketExceptionHandler();
    this.clustermarketOAuthService = clustermarketOAuthService;
    this.clustermarketService = clustermarketService;
  }

  @ExceptionHandler()
  public ResponseEntity<String> handleExceptions(Exception e) {
    return clustermarketExceptionHandler.handle(e);
  }

  @GetMapping("/bookings")
  public JsonNode getBookings(Principal principal) {
    User user = userManager.getAuthenticatedUserInSession();
    String accessToken =
        clustermarketOAuthService.getExistingAccessTokenAndRefreshIfExpired(principal);
    return client.getBookings(user, accessToken);
  }

  // Using PUT request so that bookingIDs can be sent in request BODY
  // AXIOS does not allow GET request to have BODY AND RESTful APIs should ignore any BODY in a GET
  // We may have more than the allowed limit for a Rest URL if we use a GET request - can have
  // hundreds of booking IDs
  @PutMapping("/bookings/details")
  public List<JsonNode> getBookingDetails(@RequestBody String ids) throws JsonProcessingException {
    User user = userManager.getAuthenticatedUserInSession();
    String[] idsArray = getIdsArray(ids, "bookingIDs");
    return clustermarketService.getBookingDetails(idsArray, user);
  }

  @NotNull
  private String[] getIdsArray(String ids, String idsKey) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, String> idsMap = mapper.readValue(ids, Map.class);
    String idsString = idsMap.get(idsKey);
    String[] idsArray = idsString.split(",");
    return idsArray;
  }

  // Using PUT request so that equipmentIDs can be sent in request BODY
  // AXIOS does not allow GET request to have BODY AND RESTful APIs should ignore any BODY in a GET
  // We may have more than the allowed limit for a Rest URL if we use a GET request - can have
  // hundreds of equipement IDs
  @PutMapping("/equipment/details")
  public List<JsonNode> getEquipmentDetails(@RequestBody String ids)
      throws JsonProcessingException {
    User user = userManager.getAuthenticatedUserInSession();
    String[] idsArray = getIdsArray(ids, "equipmentIDs");
    return clustermarketService.getEquipmentDetails(idsArray, user);
  }
}
