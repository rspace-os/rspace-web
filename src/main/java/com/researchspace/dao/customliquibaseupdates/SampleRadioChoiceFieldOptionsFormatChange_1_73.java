package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryChoiceFieldDef;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import java.util.Arrays;
import java.util.List;
import liquibase.database.Database;
import org.apache.commons.lang3.StringUtils;

/**
 * RSINV_150 - change stored format of radio/choice options from &= concatenated format i.e.
 * 'opt=a&opt=b', to json array format i.e. '["a","b"]'.
 */
public class SampleRadioChoiceFieldOptionsFormatChange_1_73 extends AbstractCustomLiquibaseUpdater {

  private int radioFieldsCounter;
  private int choiceFieldsCounter;

  @Override
  public String getConfirmationMessage() {
    return "Converted "
        + radioFieldsCounter
        + " sample radio fields and "
        + choiceFieldsCounter
        + " sample choice fields to new format.";
  }

  @Override
  protected void doExecute(Database database) {
    logger.info("Executing sample choice & radio field format update");

    // process radio field definitions
    List<InventoryRadioField> sampleRadioFields = getRadioFields();
    radioFieldsCounter = sampleRadioFields.size();
    logger.info("There are {} radio fields to process", radioFieldsCounter);

    for (InventoryRadioField radioField : sampleRadioFields) {
      // read all radio options from definition, update if necessary
      InventoryRadioFieldDef radioDef = radioField.getRadioDef();
      String radioOptions = radioDef.getRadioOptionsDBString();
      if (isNonEmptyOldFormatOptions(radioOptions)) {
        radioDef.setRadioOptionsList(convertOldOptionsStringToOptionList(radioOptions));
      }
      // for radio field selected option is stored as a simple string, no need to update
    }
    logger.info("Radio field definitions converted fine");

    // process choice field definitions and selected options content
    List<InventoryChoiceField> sampleChoiceFields = getChoiceFields();
    choiceFieldsCounter = sampleChoiceFields.size();
    logger.info("There are {} choice fields to process", choiceFieldsCounter);

    for (InventoryChoiceField choiceField : sampleChoiceFields) {
      InventoryChoiceFieldDef choiceDef = choiceField.getChoiceDef();
      // read all choice options from definition, update if necessary
      String choiceOptions = choiceDef.getChoiceOptionsDBString();
      if (isNonEmptyOldFormatOptions(choiceOptions)) {
        choiceDef.setChoiceOptionsList(convertOldOptionsStringToOptionList(choiceOptions));
      }
      // update choice field selected options if necessary
      String selectedChoiceOptions = choiceField.getData();
      if (isNonEmptyOldFormatOptions(selectedChoiceOptions)) {
        choiceField.setSelectedOptions(convertOldOptionsStringToOptionList(selectedChoiceOptions));
      }
    }
    logger.info("Choice field definitions and selected options converted fine");
  }

  @SuppressWarnings({"deprecation", "unchecked"})
  protected List<InventoryRadioField> getRadioFields() {
    return sessionFactory.getCurrentSession().createCriteria(InventoryRadioField.class).list();
  }

  @SuppressWarnings({"deprecation", "unchecked"})
  protected List<InventoryChoiceField> getChoiceFields() {
    return sessionFactory.getCurrentSession().createCriteria(InventoryChoiceField.class).list();
  }

  private boolean isNonEmptyOldFormatOptions(String options) {
    return !StringUtils.isBlank(options)
        && !(options.startsWith("[") && options.endsWith("]"))
        && options.contains("=");
  }

  private List<String> convertOldOptionsStringToOptionList(String oldOptions) {
    // in content like: "fieldName=valueA&fieldName=valueB" find what "fieldName" is
    String fieldNameInContent = oldOptions.substring(0, oldOptions.indexOf("="));
    // change string like "fieldName=valueA&fieldName=valueB" to
    // "&fieldName=valueA&fieldName=valueB"
    String oldOptionsPrefixed = "&" + oldOptions;
    // split string like "&fieldName=valueA&fieldName=valueB" with "&fieldName=" as separator
    String[] options =
        StringUtils.splitByWholeSeparator(oldOptionsPrefixed, "&" + fieldNameInContent + "=");

    return Arrays.asList(options);
  }
}
