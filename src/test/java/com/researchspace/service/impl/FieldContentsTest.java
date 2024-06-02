package com.researchspace.service.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.linkedelements.FieldContentDelta;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.record.TestFactory;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;

public class FieldContentsTest {

  @Before
  public void setUp() throws Exception {}

  @Test
  public void testComputeDelta() throws IOException {
    RSChemElement c1 = TestFactory.createChemElement(1L, 1L);
    RSChemElement c2 = TestFactory.createChemElement(1L, 2L);

    FieldContents original = new FieldContents();
    FieldContents newest = new FieldContents();

    FieldContentDelta delta = original.computeDelta(newest);
    // initial setup
    assertFalse(delta.getRemoved().hasAnyElements());
    assertFalse(delta.getAdded().hasAnyElements());
    assertTrue(delta.isUnchanged());

    // we'll simulate a removal of c2
    original.addElement(c1, "", RSChemElement.class);
    original.addElement(c2, "", RSChemElement.class);
    newest.addElement(c1, "", RSChemElement.class);
    delta = original.computeDelta(newest);
    assertTrue(delta.getRemoved().hasAnyElements());
    assertTrue(delta.getRemoved().getElements(RSChemElement.class).getElements().contains(c2));
    assertFalse(delta.getAdded().hasAnyElements());
    assertFalse(delta.isUnchanged());

    // now we'll simulate addition of c2
    FieldContents original2 = new FieldContents();
    FieldContents newest2 = new FieldContents();
    original2.addElement(c1, "", RSChemElement.class);
    newest2.addElement(c2, "", RSChemElement.class);
    newest2.addElement(c1, "", RSChemElement.class);
    delta = original2.computeDelta(newest2);
    assertFalse(delta.isUnchanged());
    assertFalse(delta.getRemoved().hasAnyElements());

    assertTrue(delta.getAdded().hasAnyElements());
    assertTrue(delta.getAdded().getElements(RSChemElement.class).getElements().contains(c2));

    // remove c1, and add c2, i.e., swap 1 for another
    original = new FieldContents();
    newest = new FieldContents();
    original.addElement(c1, "", RSChemElement.class);
    newest.addElement(c2, "", RSChemElement.class);
    delta = original.computeDelta(newest);
    assertFalse(delta.isUnchanged());
    assertTrue(delta.getRemoved().hasAnyElements());
    assertTrue(delta.getRemoved().getElements(RSChemElement.class).getElements().contains(c1));
    assertTrue(delta.getAdded().hasAnyElements());
    assertTrue(delta.getAdded().getElements(RSChemElement.class).getElements().contains(c2));
  }
}
