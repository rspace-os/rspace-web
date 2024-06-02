package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.researchspace.api.v1.controller.BasketsApiController.ApiBasketPost;
import com.researchspace.api.v1.model.ApiBasket;
import com.researchspace.api.v1.model.ApiBasketInfo;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiGlobalIdsRequest;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;

public class BasketsApiControllerTest extends SpringTransactionalTest {

  @Autowired private BasketsApiController basketsApi;

  private BindingResult mockBindingResult = mock(BindingResult.class);

  @Test
  public void createModifyDeleteBasket() throws Exception {

    User testUser = createInitAndLoginAnyUser();
    List<ApiBasketInfo> userBaskets = basketsApi.getBasketsForUser(testUser);
    assertEquals(0, userBaskets.size());

    ApiContainer basicContainer = createBasicContainerForUser(testUser);
    ApiBasketPost basketCreatePost = new ApiBasketPost();
    basketCreatePost.setName("test basket");
    basketCreatePost.setGlobalIds(List.of(basicContainer.getGlobalId()));

    ApiBasket createdBasket =
        basketsApi.createNewBasket(basketCreatePost, mockBindingResult, testUser);
    userBaskets = basketsApi.getBasketsForUser(testUser);
    assertEquals(1, userBaskets.size());
    assertEquals(createdBasket.getGlobalId(), userBaskets.get(0).getGlobalId());
    assertEquals(1, userBaskets.get(0).getItemCount());

    ApiBasket retrievedBasket = basketsApi.getBasketById(createdBasket.getId(), testUser);
    assertEquals(1, retrievedBasket.getItems().size());
    ApiInventoryRecordInfo containerInBasketInfo = retrievedBasket.getItems().get(0);
    assertEquals(basicContainer.getGlobalId(), containerInBasketInfo.getGlobalId());
    assertEquals(3, containerInBasketInfo.getPermittedActions().size());

    // add/remove item (works with deleted)
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    sampleApiMgr.markSampleAsDeleted(basicSample.getId(), false, testUser);

    ApiGlobalIdsRequest globalIdsRequestWithBasicSample =
        new ApiGlobalIdsRequest(List.of(basicSample.getGlobalId()));
    ApiBasket updatedBasket =
        basketsApi.addItemsToBasket(
            createdBasket.getId(), globalIdsRequestWithBasicSample, mockBindingResult, testUser);
    assertEquals(2, updatedBasket.getItemCount());
    updatedBasket =
        basketsApi.removeItemsFromBasket(
            createdBasket.getId(), globalIdsRequestWithBasicSample, mockBindingResult, testUser);
    assertEquals(1, updatedBasket.getItemCount());
  }
}
