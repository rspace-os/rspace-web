package com.researchspace.model.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.regex.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GroupListResultTest {

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testRegexp() {
    Matcher m = GroupListResult.INPUT_VALUE_ITEM.matcher("name <123>");
    assertTrue(m.matches());

    m = GroupListResult.INPUT_VALUE_ITEM.matcher("name <1abc>");
    assertFalse(m.matches());
  }

  @Test
  public void testPArseAutcompleteInput() {
    Matcher m = GroupListResult.INPUT_VALUE_ITEM.matcher("name <123>, name2<567>");
    assertTrue(m.matches());
    String input1 = "name1<123>, name2<567>";
    Set<Long> ids = GroupListResult.getGroupIdsfromMultiGroupAutocomplete(input1);
    assertEquals(2, ids.size());
    assertTrue(ids.contains(567L));

    String[] singleGroupInputs = new String[] {"name1<123>,", "name1<123>, "};
    for (String in : singleGroupInputs) {
      assertTrue(GroupListResult.validateMultiGroupAutocompleteInput(in));
      Set<Long> ids2 = GroupListResult.getGroupIdsfromMultiGroupAutocomplete(in);
      assertEquals(1, ids2.size());
    }

    String[] inputs =
        new String[] {
          "name1<123>, name2<567>,",
          "name1<123>, name2<567>",
          "name1<1>, name2<567>",
          "name2 <123>, name2 <567>"
        };
    for (String in : inputs) {
      assertTrue(GroupListResult.validateMultiGroupAutocompleteInput(in));
      Set<Long> ids2 = GroupListResult.getGroupIdsfromMultiGroupAutocomplete(in);
      assertEquals(2, ids2.size());
      assertTrue(ids2.contains(567L));
    }
  }
}
