package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiV2DeclaredArrayReadTest {
  @Test
  void listAndGetUseTheAuthenticatedCallerForEmbeddedChildAccess() {
    User owner = new User("owner");
    User other = new User("other");
    var rows =
        CollectionFieldTypes.array(
            Row.class,
            List.of(
                Field.embedded("name", CollectionFieldTypes.text(), Row::name).required(),
                Field.embedded("secret", CollectionFieldTypes.text(), Row::secret)
                    .required()
                    .readableBy(AccessFunction.when(context -> context.user() == owner))),
            values -> new Row((String) values.get("name"), (String) values.get("secret")));
    var items =
        new CollectionDescription<>(
            "items",
            Item.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Item::id),
                Field.readOnly("rows", "rows", rows, Item::rows)),
            "id",
            List.of(new Sort("id", true)));
    Item item = new Item(1L, List.of(new Row("public", "private")));
    var operations =
        new ResourceOperations<Item, Long>() {
          @Override
          public ResourcePage<Item> find(ResourceRequest request, User subject) {
            return new ResourcePage<>(List.of(item), 1);
          }

          @Override
          public long count(ResourceRequest request, User subject) {
            return 1;
          }

          @Override
          public Optional<Item> findById(Long id, User subject) {
            return Optional.of(item);
          }
        };
    var dispatcher =
        new ApiV2CrudDispatcher<>(items, new ResourceRegistry(List.of(items)), operations);
    ResourceRequest request = ResourceRequest.unpaged(null);
    for (User actor : List.of(owner, other)) {
      Map<String, Object> expected =
          Map.of(
              "id",
              1L,
              "rows",
              List.of(
                  actor == owner
                      ? Map.of("name", "public", "secret", "private")
                      : Map.of("name", "public")));
      assertEquals(expected, dispatcher.get(1L, request, actor));
      assertEquals(List.of(expected), dispatcher.list(request, actor).docs());
    }
  }

  private record Row(String name, String secret) {}

  private record Item(Long id, List<Row> rows) {}
}
