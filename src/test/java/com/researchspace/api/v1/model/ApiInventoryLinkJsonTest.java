package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.researchspace.core.util.JacksonUtil;
import org.junit.jupiter.api.Test;

/**
 * The lenient-import flag is the one thing standing between an API client and the narrowed target
 * check, and nothing else pins that it cannot arrive over the wire.
 */
public class ApiInventoryLinkJsonTest {

  @Test
  void skipTargetCheckCannotBeSetFromJson() {
    ApiInventoryLink link =
        JacksonUtil.fromJson(
            "{\"relationType\":\"Cites\",\"targetGlobalId\":\"SA1\",\"skipTargetCheck\":true}",
            ApiInventoryLink.class);

    assertEquals("SA1", link.getTargetGlobalId());
    assertFalse(link.isSkipTargetCheck());
  }

  @Test
  void skipTargetCheckIsNotSerialisedToJson() {
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType("Cites");
    link.setTargetGlobalId("SA1");
    link.setSkipTargetCheck(true);

    assertFalse(JacksonUtil.toJson(link).contains("skipTargetCheck"));
  }
}
