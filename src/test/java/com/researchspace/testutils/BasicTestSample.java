package com.researchspace.testutils;

import static com.researchspace.testutils.ScienceTestUtils.anyProteinLongName;
import static com.researchspace.testutils.ScienceTestUtils.anyProteinShortName;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import java.io.IOException;
import java.util.ArrayList;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.lang3.RandomUtils;

@Value
@EqualsAndHashCode(callSuper = false)
@Builder
public class BasicTestSample extends InventoryTestSample {

  private String name, description, tags;

  /**
   * This completely depends on the field structure of the BasicSample template we are using in
   * production/dev-testing Creates a sample of random volume 5-500ml realistic name and description
   *
   * @param templateId
   * @return
   */
  public InventorySamplePost toInventorySamplePost(Long templateId) {
    InventorySamplePost sp = new InventorySamplePost();
    sp.setName(name);
    sp.setDescription(description);
    sp.setTags(tags);
    sp.setTemplateId(templateId);
    sp.setQuantity(new QuantityPost(RandomUtils.nextInt(5, 500)));
    sp.setFields(new ArrayList<>());
    return sp;
  }

  public static BasicTestSample createBasicSample() {
    String name, longName;
    try {
      name = anyProteinShortName();
      longName = anyProteinLongName();
    } catch (IOException e) {
      throw new IllegalStateException();
    }
    return BasicTestSample.builder()
        .name("anti-" + name)
        .description(longName)
        .tags(randomAlphabetic(10))
        .build();
  }
}
