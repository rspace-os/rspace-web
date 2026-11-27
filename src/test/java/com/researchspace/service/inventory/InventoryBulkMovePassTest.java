package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryMoveEvent;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.ContainerLocation;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InventoryBulkMovePassTest {

  @Test
  void stagingRemovalsShareOneRenumberAndPreserveMoveSnapshots() {
    User user = TestFactory.createAnyUser("owner");
    Container source = container(1L, ContainerType.LIST, user);
    Container destination = container(2L, ContainerType.LIST, user);
    Container workbench = container(3L, ContainerType.WORKBENCH, user);
    Container a = container(4L, ContainerType.LIST, user);
    Container b = container(5L, ContainerType.LIST, user);
    Container retained = container(6L, ContainerType.LIST, user);
    a.moveToNewParent(source);
    b.moveToNewParent(source);
    retained.moveToNewParent(source);
    Container retainedAtDestination = container(7L, ContainerType.LIST, user);
    retainedAtDestination.moveToNewParent(destination);
    retainedAtDestination.getParentLocation().setCoordX(4);
    Container outer = container(8L, ContainerType.LIST, user);
    Container lead = container(9L, ContainerType.LIST, user);
    lead.moveToNewParent(outer);
    source.moveToNewParent(outer);
    for (Container container : List.of(source, destination, outer)) {
      for (int i = 0; i < container.getLocations().size(); i++) {
        container.getLocations().get(i).setId((container.getId() * 10) + i);
      }
    }

    ContainerLocation original = retained.getParentLocation();
    ContainerLocation retainedLocation = spy(original);
    source.getLocations().set(source.getLocations().indexOf(original), retainedLocation);
    retained.setParentLocation(retainedLocation);

    InventoryRecordRetriever records = mock(InventoryRecordRetriever.class);
    when(records.getContainerIfExists(a.getId())).thenReturn(a);
    when(records.getContainerIfExists(b.getId())).thenReturn(b);
    when(records.getContainerIfExists(lead.getId())).thenReturn(lead);
    when(records.getContainerIfExists(destination.getId())).thenReturn(destination);
    when(records.getWorkbenchForUser(user)).thenReturn(workbench);
    InventoryPermissionUtils permissions = mock(InventoryPermissionUtils.class);
    when(permissions.canUserEditInventoryRecord(destination, user)).thenReturn(true);
    InventoryMoveHelper mover = new InventoryMoveHelper();
    ReflectionTestUtils.setField(mover, "invRecRetriever", records);
    ReflectionTestUtils.setField(mover, "invPermissions", permissions);
    List<Integer> originalParentCoords = new ArrayList<>();
    mover.setPublisher(
        event -> {
          if (event instanceof InventoryMoveEvent moveEvent
              && moveEvent.getSourceItem() == source) {
            originalParentCoords.add(source.getParentLocation().getCoordX());
          }
        });

    var moved =
        mover.runBulkRecordMove(
            List.of(move(a, destination), move(b, destination), move(lead, destination)), user);
    assertEquals(3, moved.size());
    assertEquals(List.of(1, 1), originalParentCoords);
    assertEquals(4, retainedAtDestination.getParentLocation().getCoordX());
    assertEquals(5, ((ApiContainerInfo) moved.get(0)).getParentLocation().getCoordX());
    assertEquals(6, ((ApiContainerInfo) moved.get(1)).getParentLocation().getCoordX());
    assertEquals(a.getId(), moved.get(0).getId());
    assertEquals(b.getId(), moved.get(1).getId());
    assertEquals(2, moved.get(0).getParentContainer().getContentSummary().getTotalCount());
    assertEquals(3, moved.get(1).getParentContainer().getContentSummary().getTotalCount());
    assertEquals(1, retained.getParentLocation().getCoordX());
    assertEquals(1, source.getContentCount());
    assertEquals(4, destination.getContentCount());
    verify(retainedLocation).setCoordX(anyInt());
  }

  private Container container(long id, ContainerType type, User user) {
    Container container = new Container(type);
    container.setId(id);
    container.setOwner(user);
    container.setName("Container " + id);
    return container;
  }

  private ApiContainerInfo move(Container record, Container target) {
    ApiContainerInfo move = new ApiContainerInfo();
    move.setId(record.getId());
    ApiContainerInfo parent = new ApiContainerInfo();
    parent.setId(target.getId());
    move.setParentContainer(parent);
    return move;
  }
}
