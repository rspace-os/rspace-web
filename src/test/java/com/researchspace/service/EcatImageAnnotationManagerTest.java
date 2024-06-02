package com.researchspace.service;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class EcatImageAnnotationManagerTest extends SpringTransactionalTest {

  @Autowired private EcatImageAnnotationManager mgr;

  @Test
  public void testRemovingImageNodesFromAnnotation() throws Exception {

    String exampleAnnotation =
        "zwibbler3.[{\"id\":0,\"type\":\"GroupNode\",\"fillStyle\":\"#cccccc\",\"strokeStyle\":\"#000000\","
            + "\"lineWidth\":2,\"shadow\":false,\"matrix\":[1,0,0,1,0,0],\"layer\":\"default\"},{\"id\":1,\"type\":\"PageNode\","
            + "\"parent\":0,\"fillStyle\":\"#cccccc\",\"strokeStyle\":\"#000000\",\"lineWidth\":2,\"shadow\":false,\"matrix\":[1,0,0,1,0,0],"
            + "\"layer\":\"default\"},"
            + "{\"id\":2,\"type\":\"ImageNode\",\"parent\":1,\"fillStyle\":\"#cccccc\",\"strokeStyle\":"
            + "\"#000000\",\"lineWidth\":2,\"shadow\":false,\"matrix\":[1,0,0,1,0,0],\"layer\":\"default\","
            + "\"url\":\"/image/getImageToAnnotate/131072-115/1\",\"locked\":true},"
            + "{\"id\":3,\"type\":\"PathNode\",\"parent\":1,\"fillStyle\":\"rgba(255,255,255,0.5)\","
            + "\"strokeStyle\":\"#000000\",\"lineWidth\":2,\"shadow\":false,\"matrix\":[1.558717421471897,0,0,1.558717421471897,147,133],"
            + "\"layer\":\"default\",\"textFillStyle\":\"#000000\",\"fontName\":\"Arial\",\"fontSize\":20,\"dashes\":\"\",\"smoothness\":0.3,"
            + "\"sloppiness\":0,\"closed\":true,\"arrowSize\":0,\"arrowStyle\":\"simple\",\"doubleArrow\":false,\"text\":\"\","
            + "\"roundRadius\":0,\"commands\":[0,0,-50,6,50,0,50,-50,6,0,50,50,50,6,-50,0,-50,50,6,0,-50,-50,-50,7],\"seed\":39169},"
            + "{\"id\":4,\"type\":\"ImageNode\",\"parent\":1,\"fillStyle\":\"#cccccc\",\"strokeStyle\":\"#000000\",\"lineWidth\":2,"
            + "\"shadow\":false,\"matrix\":[1,0,0,1,418.5,155.5],\"layer\":\"default\",\"url\":\"/scripts/zwibbler2/icon4.png\"}]";

    String expectedJsonPart =
        "[{\"id\":0,\"type\":\"GroupNode\",\"fillStyle\":\"#cccccc\",\"strokeStyle\":\"#000000\","
            + "\"lineWidth\":2,\"shadow\":false,\"matrix\":[1,0,0,1,0,0],\"layer\":\"default\"},{\"id\":1,\"type\":\"PageNode\","
            + "\"parent\":0,\"fillStyle\":\"#cccccc\",\"strokeStyle\":\"#000000\",\"lineWidth\":2,\"shadow\":false,\"matrix\":[1,0,0,1,0,0],"
            + "\"layer\":\"default\"},"
            + "{\"id\":3,\"type\":\"PathNode\",\"parent\":1,\"fillStyle\":\"rgba(255,255,255,0.5)\","
            + "\"strokeStyle\":\"#000000\",\"lineWidth\":2,\"shadow\":false,\"matrix\":[1.558717421471897,0,0,1.558717421471897,147,133],"
            + "\"layer\":\"default\",\"textFillStyle\":\"#000000\",\"fontName\":\"Arial\",\"fontSize\":20,\"dashes\":\"\",\"smoothness\":0.3,"
            + "\"sloppiness\":0,\"closed\":true,\"arrowSize\":0,\"arrowStyle\":\"simple\",\"doubleArrow\":false,\"text\":\"\","
            + "\"roundRadius\":0,\"commands\":[0,0,-50,6,50,0,50,-50,6,0,50,50,50,6,-50,0,-50,50,6,0,-50,-50,-50,7],\"seed\":39169},"
            + "{\"id\":4,\"type\":\"ImageNode\",\"parent\":1,\"fillStyle\":\"#cccccc\",\"strokeStyle\":\"#000000\",\"lineWidth\":2,"
            + "\"shadow\":false,\"matrix\":[1,0,0,1,418.5,155.5],\"layer\":\"default\",\"url\":\"/scripts/zwibbler2/icon4.png\"}]";

    String result = mgr.removeBackgroundImageNodesFromZwibblerAnnotation(exampleAnnotation);
    String resultJsonPart = result.substring("zwibbler3.".length());

    JsonNode expectedJson = new ObjectMapper().readTree(expectedJsonPart);
    Object resultJson = new ObjectMapper().readTree(resultJsonPart);

    assertEquals(expectedJson, resultJson);
  }
}
