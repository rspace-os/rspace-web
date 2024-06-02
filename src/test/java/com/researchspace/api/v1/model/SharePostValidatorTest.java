package com.researchspace.api.v1.model;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.Assert.*;

import com.researchspace.core.testutil.JavaxValidatorTest;
import java.util.Collections;
import org.junit.Test;

public class SharePostValidatorTest extends JavaxValidatorTest {

  @Test
  public void sharePostValidation() {
    SharePost invalid = new SharePost(toList(1L), null, null);
    assertNErrors(invalid, 1);
    invalid = new SharePost(toList(1L), Collections.emptyList(), null);
    assertNErrors(invalid, 1);
    invalid = new SharePost(toList(1L), null, Collections.emptyList());
    assertNErrors(invalid, 1);
    invalid = new SharePost(toList(1L), Collections.emptyList(), Collections.emptyList());
    assertNErrors(invalid, 1);
    SharePost valid = new SharePost(toList(1L), null, toList(new UserSharePostItem(2L, "edit")));
    assertValid(valid);

    valid = new SharePost(toList(1L), toList(new GroupSharePostItem(3L, "edit", 4L)), null);
    assertValid(valid);
  }
}
