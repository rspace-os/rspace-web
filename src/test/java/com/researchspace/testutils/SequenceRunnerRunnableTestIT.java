package com.researchspace.testutils;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.Invokable;
import com.researchspace.core.testutil.SequencedRunnableRunner;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.impl.ShiroTestUtils;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.shiro.SecurityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Example test cases for how to run multiple stepwise tests. */
public class SequenceRunnerRunnableTestIT extends RealTransactionSpringTestBase {

  ShiroTestUtils shiroUtils;

  @Before
  public void setUp() throws Exception {
    shiroUtils = new ShiroTestUtils();
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    shiroUtils.clearSubject();
  }

  static Invokable createInvokable(final int i) {
    return new Invokable() {
      public void invoke() throws Exception {
        //	System.out.println(" invoking " + i);
      }
    };
  }

  @Test
  public void testUserActions() throws Exception {
    // initialise all the users we need before starting tests
    final User u1 = createAndSaveUser("shiro1" + RandomStringUtils.randomAlphabetic(5));
    final User u2 = createAndSaveUser("shiro2" + RandomStringUtils.randomAlphabetic(5));
    final User u3 = createAndSaveUser("shiro3" + RandomStringUtils.randomAlphabetic(5));
    initUsers(u1, u2, u3);

    // Each invokable is just a callback function that will execute an atomic sequence of operations
    // in a single thread. While an invokable is running, all other threads are stopped.
    // We define all the invokables up front, before running any.
    // the first invokable for a user should log them in
    Invokable[] invokables = new Invokable[6];
    invokables[0] =
        new Invokable() {
          // annotate these inner class methods with @Test
          @Test
          public void invoke() throws Exception {
            shiroUtils.doLogin(u1);
            StructuredDocument sd = createBasicDocumentInRootFolderWithText(u1, "some text");
            // just put usual assertions in here.
            assertTrue(sd.getFieldCount() == 1);
            System.err.println("o");
          }
        };
    invokables[1] =
        new Invokable() {
          public void invoke() throws Exception {
            shiroUtils.doLogin(u2);
            System.err.println("1");
          }
        };
    invokables[2] =
        new Invokable() {
          public void invoke() throws Exception {
            shiroUtils.doLogin(u3);
            System.err.println("2");
          }
        };

    // the last invokables for a user should remember to log them out.
    invokables[3] =
        new Invokable() {
          public void invoke() throws Exception {
            createBasicDocumentInRootFolderWithText(u1, "u1");
            SecurityUtils.getSubject().logout();
            System.err.println("3");
          }
        };
    invokables[4] =
        new Invokable() {
          public void invoke() throws Exception {
            createAnyForm(u2);
            SecurityUtils.getSubject().logout();
            System.err.println("4");
          }
        };
    invokables[5] =
        new Invokable() {
          public void invoke() throws Exception {
            createBasicDocumentInRootFolderWithText(u3, "u3");
            SecurityUtils.getSubject().logout();
            System.err.println("5");
          }
        };
    // the configuration defines how many threads we want.
    // All operations performed by a user should occur in a single thread
    Map<String, Integer[]> config = new TreeMap<>();
    // these are the array indices of the Invokable []. Note all invokables in an array.
    config.put("t1", new Integer[] {0, 3});
    config.put(
        "t2",
        new Integer[] {
          1, 5,
        });
    config.put("t3", new Integer[] {2, 4});
    SequencedRunnableRunner runner = new SequencedRunnableRunner(config, invokables);
    runner.runSequence();
    // System.out.println("Finished");
  }

  @Test
  public void testSequenceRunner() throws Exception {
    Invokable[] invokables = new Invokable[20];
    for (int i = 0; i < 20; i++) {
      invokables[i] = createInvokable(i);
    }
    Map<String, Integer[]> config = new TreeMap<>();
    config.put("t1", new Integer[] {0, 3, 5, 9, 11, 14, 17, 19});
    config.put("t2", new Integer[] {1, 4, 7, 10, 13, 15});
    config.put("t3", new Integer[] {2, 6, 8, 12, 16, 18});
    SequencedRunnableRunner runner = new SequencedRunnableRunner(config, invokables);
    runner.runSequence();

    System.err.println("Finished");
  }
}
