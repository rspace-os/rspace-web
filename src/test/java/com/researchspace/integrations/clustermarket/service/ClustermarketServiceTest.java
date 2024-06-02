package com.researchspace.integrations.clustermarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.integrations.clustermarket.client.ClustermarketClient;
import com.researchspace.integrations.clustermarket.model.ClustermarketBooking;
import com.researchspace.integrations.clustermarket.model.ClustermarketEquipment;
import com.researchspace.integrations.clustermarket.repository.ClustermarketBookingRepository;
import com.researchspace.integrations.clustermarket.repository.ClustermarketEquipmentRepository;
import com.researchspace.model.User;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ClustermarketServiceTest {
  @Mock private ClustermarketEquipmentRepository equipmentRepository;
  @Mock private ClustermarketBookingRepository bookingRespository;
  @Mock private User user;
  @Mock private ClustermarketClient clustermarketClient;
  @InjectMocks private ClustermarketServiceImpl service;
  private String[] ids = new String[] {"1", "2", "3"};
  private List<Long> longIDs = List.of(1L, 2L, 3L);

  private String bookingOneJson = "{\"id\":\"1\"}";
  private String bookingTwoJson = "{\"id\":\"2\"}";
  private String bookingThreeJson = "{\"id\":\"3\"}";
  private String bookingFourJson = "{\"id\":\"4\"}";
  private String equipmentOneJson = "{\"id\":\"1\"}";
  private String equipmentTwoJson = "{\"id\":\"2\"}";
  private String equipmentThreeJson = "{\"id\":\"3\"}";
  private String equipmentFourJson = "{\"id\":\"4\"}";
  private JsonNode bookingOneNode;
  private JsonNode bookingTwoNode;
  private JsonNode bookingThreeNode;
  private JsonNode bookingFourNode;
  private ClustermarketBooking bookingOne;
  private ClustermarketBooking bookingTwo;
  private ClustermarketBooking bookingThree;
  private ClustermarketBooking bookingFour;
  private List<JsonNode> bookingResults;
  private JsonNode equipmentOneNode;
  private JsonNode equipmentTwoNode;
  private JsonNode equipmentThreeNode;
  private JsonNode equipmentFourNode;
  private ClustermarketEquipment equipmentOne;
  private ClustermarketEquipment equipmentTwo;
  private ClustermarketEquipment equipmentThree;
  private ClustermarketEquipment equipmentFour;
  private List<JsonNode> equipmentResults;

  @Before
  public void setUp() throws JsonProcessingException {
    openMocks(this);
    ObjectMapper mapper = new ObjectMapper();
    bookingOneNode = mapper.readTree(bookingOneJson);
    bookingTwoNode = mapper.readTree(bookingTwoJson);
    bookingThreeNode = mapper.readTree(bookingThreeJson);
    bookingFourNode = mapper.readTree(bookingFourJson);
    equipmentOneNode = mapper.readTree(equipmentOneJson);
    equipmentTwoNode = mapper.readTree(equipmentTwoJson);
    equipmentThreeNode = mapper.readTree(equipmentThreeJson);
    equipmentFourNode = mapper.readTree(equipmentFourJson);
    bookingOne = new ClustermarketBooking(1L, bookingOneJson);
    bookingTwo = new ClustermarketBooking(2L, bookingTwoJson);
    bookingThree = new ClustermarketBooking(3L, bookingThreeJson);
    bookingFour = new ClustermarketBooking(4L, bookingFourJson);
    equipmentOne = new ClustermarketEquipment(1L, equipmentOneJson);
    equipmentTwo = new ClustermarketEquipment(2L, equipmentTwoJson);
    equipmentThree = new ClustermarketEquipment(3L, equipmentThreeJson);
    equipmentFour = new ClustermarketEquipment(4L, equipmentFourJson);
    bookingResults = List.of(bookingOneNode, bookingTwoNode, bookingThreeNode);
    equipmentResults = List.of(equipmentOneNode, equipmentTwoNode, equipmentThreeNode);
    when(clustermarketClient.getBookingDetails(eq("4"), any(User.class)))
        .thenReturn(bookingFourNode);
    when(clustermarketClient.getEquipmentDetails(eq("4"), any(User.class)))
        .thenReturn(equipmentFourNode);
    when(equipmentRepository.findByIds(any()))
        .thenReturn(List.of(equipmentOne, equipmentTwo, equipmentThree));
    when(bookingRespository.findByIds(any()))
        .thenReturn(List.of(bookingOne, bookingTwo, bookingThree));
  }

  // No tests for clustermarket client throwing 404 or returning null. The clustermarket API first
  // returns
  // a list of bookingIDs and equipmentIDs which we then call Clustermarket with to get the booking
  // details.
  // If some of these bookings don't actually exist then thats a major error and we should generate
  // a 500/log 404 at ERROR level
  @Test
  public void getBookingDetailsShouldFetchFromDBAndNotCallClustermarketIfAllDataInDB() {
    List<JsonNode> results = service.getBookingDetails(ids, user);
    verify(clustermarketClient, never()).getBookingDetails(any(String.class), any(User.class));
    assertEquals(bookingResults, results);
  }

  @Test
  public void getBookingDetailsShouldFetchFromClustermarketIfNotInDB() {
    List<JsonNode> results = service.getBookingDetails(new String[] {"1", "2", "3", "4"}, user);
    verify(clustermarketClient, times(1)).getBookingDetails(eq("4"), any(User.class));
    bookingResults = List.of(bookingOneNode, bookingTwoNode, bookingThreeNode, bookingFourNode);
    assertEquals(bookingResults, results);
  }

  @Test
  public void getBookingDetailsShouldSaveDataFetchedFromClustermarket() {
    List<JsonNode> results = service.getBookingDetails(new String[] {"1", "2", "3", "4"}, user);
    verify(bookingRespository, times(1)).save(eq(bookingFour));
  }

  @Test
  public void getEquipmentDetailsShouldFetchFromDBAndNotCallClustermarketIfAllDataInDB() {
    List<JsonNode> results = service.getEquipmentDetails(ids, user);
    verify(clustermarketClient, never()).getEquipmentDetails(any(String.class), any(User.class));
    assertEquals(equipmentResults, results);
  }

  @Test
  public void getEquipmentDetailsShouldFetchFromClustermarketIfNotInDB() {
    List<JsonNode> results = service.getEquipmentDetails(new String[] {"1", "2", "3", "4"}, user);
    verify(clustermarketClient, times(1)).getEquipmentDetails(eq("4"), any(User.class));
    equipmentResults =
        List.of(equipmentOneNode, equipmentTwoNode, equipmentThreeNode, equipmentFourNode);
    assertEquals(equipmentResults, results);
  }

  @Test
  public void getEquipmentDetailsShouldSaveDataFetchedFromClustermarket() {
    List<JsonNode> results = service.getEquipmentDetails(new String[] {"1", "2", "3", "4"}, user);
    verify(equipmentRepository, times(1)).save(eq(equipmentFour));
  }
}
