package com.researchspace.inventory.api.v2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.researchspace.model.User;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.service.inventory.ContainerReadAccess;
import com.researchspace.service.inventory.ContainerReadManager;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContainerResourceOperationsTest {
  @Test
  void batchesRelationshipDocumentsThroughTheAuthorizedPage() {
    var manager = mock(ContainerReadManager.class);
    var operations = new ContainerResourceOperations(manager, mock(ContainerReadAccess.class));
    var actor = new User();
    var bench = new Container(ContainerType.WORKBENCH);
    bench.setId(12L);
    bench.setName("Bench");
    when(manager.find(any(ResourceRequest.class), same(actor)))
        .thenReturn(new ResourcePage<>(List.of(bench), 1));
    var documents = operations.relationshipReadDocuments(Set.of(12L, 13L), actor).orElseThrow();
    assertEquals(Set.of(12L), documents.keySet());
    assertEquals("BE12", documents.get(12L).get("globalId"));
    verify(manager).find(any(ResourceRequest.class), same(actor));
  }
}
