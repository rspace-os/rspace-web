package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class SampleApiPostFullValidatorTest extends InventoryRecordValidationTestBase {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Autowired
  @Qualifier("sampleApiPostFullValidator")
  private SampleApiPostFullValidator samplePostFullValidator;

  @Before
  public void setup() {
    validator = samplePostFullValidator;
  }

  @Test
  public void validateQuantityUnit() {

    // template defining grams as a default unit
    Sample baseTemplate = new Sample();
    baseTemplate.setTemplate(true);
    baseTemplate.setDefaultUnitId(RSUnitDef.GRAM.getId());

    // incoming sample with millilitre quantity
    ApiSampleWithFullSubSamples apiSamplePost = new ApiSampleWithFullSubSamples();
    apiSamplePost.setQuantity(new ApiQuantityInfo(BigDecimal.ONE, RSUnitDef.MILLI_LITRE.getId()));

    // lets run the validation
    SamplesApiController.ApiSampleFullPost fullPost = new SamplesApiController.ApiSampleFullPost();
    fullPost.setApiSample(apiSamplePost);
    fullPost.setTemplate(baseTemplate);

    Errors e = new BeanPropertyBindingResult(apiSamplePost, "apiSample");
    validator.validate(fullPost, e);
    assertEquals(1, e.getErrorCount());
    assertFieldNameIs(e, "quantity");
    assertEquals(
        "errors.inventory.sample.unit.incompatible.with.template", e.getFieldError().getCode());

    // sanity check with comparable quantity
    fullPost
        .getApiSample()
        .setQuantity(new ApiQuantityInfo(BigDecimal.ONE, RSUnitDef.MILLI_GRAM.getId()));
    e = new BeanPropertyBindingResult(apiSamplePost, "apiSample");
    validator.validate(fullPost, e);
    assertEquals(0, e.getErrorCount());
  }

  @Test
  public void validateMandatoryFields() {

    User testUser = createInitAndLoginAnyUser();
    ApiSampleTemplate mandatoryFieldsTemplate = createSampleTemplateWithMandatoryFields(testUser);

    // check sample post with default fields (not specifying 'fields' array)
    ApiSampleWithFullSubSamples apiSamplePost = new ApiSampleWithFullSubSamples();
    SamplesApiController.ApiSampleFullPost fullPost = new SamplesApiController.ApiSampleFullPost();
    fullPost.setApiSample(apiSamplePost);
    fullPost.setTemplate(
        sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(
            mandatoryFieldsTemplate.getId(), testUser));

    // let's run the validation for incoming sample without fields (default values will be used)
    Errors e = new BeanPropertyBindingResult(apiSamplePost, "apiSample");
    validator.validate(fullPost, e);
    assertEquals(2, e.getErrorCount());
    assertFieldNameIs(e, "fields");
    assertEquals("errors.inventory.sample.mandatory.field.empty", e.getFieldError().getCode());
    assertEquals(2, e.getFieldErrors().size());
    assertEquals(
        "errors.inventory.sample.mandatory.field.empty", e.getFieldErrors().get(0).getCode());
    assertEquals(
        "myText (mandatory - no default value)", e.getFieldErrors().get(0).getArguments()[0]);
    assertEquals(
        "errors.inventory.sample.mandatory.field.no.selection",
        e.getFieldErrors().get(1).getCode());
    assertEquals(
        "myRadio (mandatory - no default value)", e.getFieldErrors().get(1).getArguments()[0]);

    // re-run validation with the 'fields' array, but with empty field content
    apiSamplePost.setFields(
        Stream.generate(ApiSampleField::new).limit(6).collect(Collectors.toList()));
    e = new BeanPropertyBindingResult(apiSamplePost, "apiSample");
    validator.validate(fullPost, e);
    assertEquals(4, e.getErrorCount());
    assertFieldNameIs(e, "fields");
    assertEquals(4, e.getFieldErrors().size());
    assertEquals(
        "myText (mandatory - with default value)", e.getFieldErrors().get(0).getArguments()[0]);
    assertEquals(
        "myText (mandatory - no default value)", e.getFieldErrors().get(1).getArguments()[0]);
    assertEquals(
        "myRadio (mandatory - with default value)", e.getFieldErrors().get(2).getArguments()[0]);
    assertEquals(
        "myRadio (mandatory - no default value)", e.getFieldErrors().get(3).getArguments()[0]);

    // sanity check with valid non-empty field data
    apiSamplePost.getFields().get(0).setContent("test content");
    apiSamplePost.getFields().get(1).setContent("test content");
    apiSamplePost.getFields().get(3).setSelectedOptions(List.of("a"));
    apiSamplePost.getFields().get(4).setSelectedOptions(List.of("b"));
    e = new BeanPropertyBindingResult(apiSamplePost, "apiSample");
    validator.validate(fullPost, e);
    assertEquals(0, e.getErrorCount());
  }
}
