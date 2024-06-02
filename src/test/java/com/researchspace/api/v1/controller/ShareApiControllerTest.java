package com.researchspace.api.v1.controller;

import static java.util.Collections.emptyList;

import com.researchspace.api.v1.model.GroupSharePostItem;
import com.researchspace.api.v1.model.SharePost;
import com.researchspace.api.v1.model.UserSharePostItem;
import com.researchspace.core.testutil.JavaxValidatorTest;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.RecordSharingManager;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ShareApiControllerTest extends JavaxValidatorTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock RecordSharingManager handler;

  @InjectMocks ShareApiController controller;
  User sharer = TestFactory.createAnyUser("any");

  @Test
  public void testShareItems() {
    SharePost post = createValidSharePost();
    assertNErrors(post, 0);
    setPermission(post, "EDIT");
    assertNErrors(post, 0);
    setPermission(post, "edit");
    assertNErrors(post, 0);

    post.setItemsToShare(Collections.emptyList());
    assertNErrors(post, 1);

    post = createValidSharePost();
    setPermission(post, "unknown permission type");
    assertNErrors(post, 1);

    // >= 1 user or groups required
    post = createValidSharePost();
    post.setGroupSharePostItems(emptyList());
    post.setUserSharePostItems(emptyList());

    assertNErrors(post, 1);
  }

  private void setPermission(SharePost post, String perm) {
    post.getGroupSharePostItems().get(0).setPermission(perm);
  }

  static SharePost createValidSharePost() {
    return SharePost.builder()
        .itemToShare(1L)
        .groupSharePostItem(
            GroupSharePostItem.builder().id(2L).permission("READ").sharedFolderId(3L).build())
        .userSharePostItem(UserSharePostItem.builder().id(2L).permission("EDIT").build())
        .build();
  }
}
