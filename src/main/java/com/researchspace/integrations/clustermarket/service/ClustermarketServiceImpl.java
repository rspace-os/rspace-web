package com.researchspace.integrations.clustermarket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.integrations.clustermarket.client.ClustermarketClient;
import com.researchspace.integrations.clustermarket.model.ClustermarketBooking;
import com.researchspace.integrations.clustermarket.model.ClustermarketData;
import com.researchspace.integrations.clustermarket.model.ClustermarketEquipment;
import com.researchspace.integrations.clustermarket.repository.ClustermarketBookingRepository;
import com.researchspace.integrations.clustermarket.repository.ClustermarketEquipmentRepository;
import com.researchspace.model.User;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClustermarketServiceImpl implements ClustermarketService {
  // Arbitrary number chosen to protect Clustermarket from too many requests
  public static final int MAX_POOL_SIZE = 10;
  private final ClustermarketClient client;
  private final ClustermarketBookingRepository clustermarketBookingRepository;
  private final ClustermarketEquipmentRepository clustermarketEquipmentRepository;
  private ObjectMapper objectMapper = new ObjectMapper();

  public ClustermarketServiceImpl(
      ClustermarketClient client,
      ClustermarketBookingRepository clustermarketBookingRepository,
      ClustermarketEquipmentRepository clustermarketEquipmentRepository) {
    this.client = client;
    this.clustermarketBookingRepository = clustermarketBookingRepository;
    this.clustermarketEquipmentRepository = clustermarketEquipmentRepository;
  }

  @Transactional
  public List<JsonNode> getBookingDetails(String[] ids, User user) {
    return getData(clustermarketBookingRepository::findByIds, this::getAndSaveBooking, ids, user);
  }

  @Transactional
  public List<JsonNode> getEquipmentDetails(String[] ids, User user) {
    return getData(
        clustermarketEquipmentRepository::findByIds, this::getAndSaveEquipment, ids, user);
  }

  private List<JsonNode> getData(
      Function<List<Long>, List<? extends ClustermarketData>> dbCall,
      BiFunction<Long, User, JsonNode> remoteCall,
      String[] ids,
      User user) {
    List<Long> longIDs =
        Arrays.stream(ids).map(id -> Long.parseLong(id)).collect(Collectors.toList());
    Map<Long, ClustermarketData> dbResults =
        dbCall.apply(longIDs).stream()
            .collect(Collectors.toMap(ClustermarketData::getId, Function.identity()));
    List<Long> unmatchedIDs = new ArrayList<>();
    List<JsonNode> resultsJson =
        dbResults.values().stream().map(this::toJsonNode).collect(Collectors.toList());
    for (Long id : longIDs) {
      if (!dbResults.keySet().contains(id)) {
        unmatchedIDs.add(id);
      }
    }
    List<JsonNode> newData = new ArrayList<>();
    if (unmatchedIDs.size() > 0) {
      newData = callClusterMarket(remoteCall, unmatchedIDs, user);
    }
    resultsJson.addAll(newData);
    return resultsJson;
  }

  private JsonNode getAndSaveBooking(Long id, User user) {
    JsonNode data = client.getBookingDetails("" + id, user);
    clustermarketBookingRepository.save(new ClustermarketBooking(id, data.toString()));
    return data;
  }

  private JsonNode getAndSaveEquipment(Long id, User user) {
    JsonNode data = client.getEquipmentDetails("" + id, user);
    clustermarketEquipmentRepository.save(new ClustermarketEquipment(id, data.toString()));
    return data;
  }

  private List<JsonNode> callClusterMarket(
      BiFunction<Long, User, JsonNode> remoteCall, List<Long> ids, User user) {
    int poolSize = ids.size() > MAX_POOL_SIZE ? MAX_POOL_SIZE : ids.size();
    ForkJoinPool customThreadPool = new ForkJoinPool(poolSize);
    try {
      return customThreadPool
          .submit(
              () ->
                  ids.stream()
                      .parallel()
                      .map(id -> remoteCall.apply(id, user))
                      .collect(Collectors.toList()))
          .get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      customThreadPool.shutdown();
    }
  }

  @SneakyThrows // anticipate that DB is corrupt if Json is unreadable, so let it cause a 500 error
  private JsonNode toJsonNode(ClustermarketData data) {
    return objectMapper.readTree(data.getData());
  }
}
