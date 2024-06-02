package com.researchspace.model.repository;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.JavaxValidatorTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Test;

public class RepoDepositConfigValidationTest extends JavaxValidatorTest {

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testPropertyValidation() {
    RepoDepositMeta meta = RepositoryTestFactory.createAValidRepoDepositMeta();
    assertNErrors(meta, 0);

    meta.setDescription("");
    assertNErrors(meta, 1);
    meta.setDescription(null);
    assertNErrors(meta, 1);
    meta.setTitle("");
    assertNErrors(meta, 3);
    meta.setTitle(null);
    assertNErrors(meta, 2);
    meta.setSubject("");
    assertNErrors(meta, 3);
    meta.setSubject(null);
    assertNErrors(meta, 3);

    // reset to valid
    meta = RepositoryTestFactory.createAValidRepoDepositMeta();
    assertNErrors(meta, 0);
    String tooLong = CoreTestUtils.getRandomName(RepoDepositMeta.MAX_FIELD_LENGTH + 1);
    meta.setDescription(tooLong);
    assertNErrors(meta, 1);
    meta.setTitle(tooLong);
    assertNErrors(meta, 2);
    meta.setSubject(tooLong);
    assertNErrors(meta, 3);
  }

  @Test
  public void testCollectionsValidation() {
    RepoDepositMeta meta = RepositoryTestFactory.createAValidRepoDepositMeta();
    meta.setAuthors(Collections.emptyList());
    assertNErrors(meta, 1);
    List<UserDepositorAdapter> users = createUsers(RepoDepositMeta.MAX_USERS + 1);
    meta.setAuthors(users);
    assertNErrors(meta, 1);
    users = users.subList(0, RepoDepositMeta.MAX_USERS);
    meta.setAuthors(users);
    assertNErrors(meta, 0);

    meta.setContacts(Collections.emptyList());
    assertNErrors(meta, 1);
    users = createUsers(RepoDepositMeta.MAX_USERS + 1);
    meta.setContacts(users);
    assertNErrors(meta, 1);
    users = users.subList(0, RepoDepositMeta.MAX_USERS);
    meta.setContacts(users);
    assertNErrors(meta, 0);
  }

  @Test
  public void testUserAdaptorValidation() {
    UserDepositorAdapter uda = new UserDepositorAdapter(null, null);
    assertNErrors(uda, 2);
    uda.setEmail("invalidemail");
    assertNErrors(uda, 2);
    uda.setEmail("validemail@y.com");
    assertNErrors(uda, 1);
    uda.setEmail("");
    assertNErrors(uda, 2);

    uda = createUsers(1).get(0);
    uda.setUniqueName(" ");
    assertNErrors(uda, 1);
    uda.setEmail(" ");
    assertNErrors(uda, 2); // blank and invalid
  }

  private List<UserDepositorAdapter> createUsers(int i) {
    List<UserDepositorAdapter> rc = new ArrayList<>();
    IntStream.range(0, i + 1)
        .forEach(
            (j) -> {
              UserDepositorAdapter uda = new UserDepositorAdapter("email" + j + "@x.com", j + "");
              rc.add(uda);
            });
    return rc;
  }
}
