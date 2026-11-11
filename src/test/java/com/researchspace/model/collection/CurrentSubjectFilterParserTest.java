package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class CurrentSubjectFilterParserTest {
  private static CollectionDescription<User> description() {
    return new CollectionDescription<>(
            "users",
            User.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), User::getId),
                Relationship.<User, User, Long>referenceToOne(
                    "requester",
                    "users",
                    CollectionFieldTypes.longNumber(),
                    User.class,
                    user -> user,
                    User::getId,
                    "id")),
            "id",
            List.of(new Sort("id", true)),
            AccessPolicy.readOnly(AccessFunction.authenticated()))
        .withFilter(new FilterSelector.Alias<>("targetOwner", "target"));
  }

  private static User subject(long id) {
    User user = new User("viewer" + id);
    user.setId(id);
    return user;
  }

  @Test
  void userRelationshipResolvesEachEffectiveSubject() {
    for (long id : List.of(12L, 27L)) {
      var parser =
          new RsqlFilterParser(
              description(), null, new RuntimeFieldContext(List.of(), subject(id)));
      assertEquals(
          new FilterExpression.Comparison("requester.value", Operator.EQUAL, List.of(id), false),
          parser.parse("requester.value==me"));
      assertValueError(() -> parser.parse("id==me"));
    }
  }

  @Test
  void missingSubjectDoesNotResolveRelationshipAlias() {
    var parser = new RsqlFilterParser(description());
    assertValueError(() -> parser.parse("requester.value==me"));
  }

  @Test
  void ownerOnlyAcceptsMeOutsideOrGroups() {
    User viewer = subject(12L);
    var parser =
        new RsqlFilterParser(description(), null, new RuntimeFieldContext(List.of(), viewer));
    assertEquals(
        new FilterExpression.Comparison("targetOwner", Operator.EQUAL, List.of(viewer), false),
        parser.parse("targetOwner==me"));
    assertValueError(() -> parser.parse("targetOwner==12"));
    assertValueError(() -> parser.parse("id==1,(id==2;targetOwner==me)"));
    assertThrows(CollectionQueryException.class, () -> parser.parse("targetOwner!=me"));
    assertValueError(() -> new RsqlFilterParser(description()).parse("targetOwner==me"));
  }

  private static void assertValueError(Executable action) {
    assertEquals(
        CollectionQueryException.Reason.VALUE,
        assertThrows(CollectionQueryException.class, action).getReason());
  }
}
