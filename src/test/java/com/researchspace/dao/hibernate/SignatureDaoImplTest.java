package com.researchspace.dao.hibernate;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.researchspace.model.Signature;
import com.researchspace.model.User;
import com.researchspace.model.Witness;
import com.researchspace.model.record.TestFactory;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SignatureDaoImplTest {

  User user = null;

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testFilterWitnessesRSPAC_1397() {
    SignatureDaoHibernateImpl impl = new SignatureDaoHibernateImpl();

    assertNull(impl.handleNonUniqueWitnesses(1L, user, Collections.emptyList()));
    Witness witness = createAWitness();
    List<Witness> results = toList(witness);
    assertEquals(witness, impl.handleNonUniqueWitnesses(1L, user, results));
    Witness witness2 = createAWitness();
    witness2.setWitnessesDate(new Date());
    results.add(witness2);
    assertEquals(witness2, impl.handleNonUniqueWitnesses(1L, user, results));
    results.set(0, witness2); // set a duplicate?
    assertEquals(witness2, impl.handleNonUniqueWitnesses(1L, user, results));
  }

  private Witness createAWitness() {
    Signature sig = TestFactory.createASignature(TestFactory.createAnySD(), user);
    Witness witness = new Witness(user);
    witness.setSignature(sig);
    return witness;
  }
}
