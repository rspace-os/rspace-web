package com.researchspace.model.audittrail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class JsonTest {

  String json =
      "{\"data\":{\"to\":{\"data\":{\"id\":6971,\"name\":\"Other"
          + " Data_Copy_Copy\"}},\"from\":{\"data\":{\"id\":6965,\"name\":\"Other Data_Copy\"}}}}";

  @Test
  public void test() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    AuditData node = mapper.readValue(json, AuditData.class);
    System.err.println(node);
  }
}
