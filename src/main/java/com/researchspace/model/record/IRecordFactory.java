package com.researchspace.model.record;

import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Basket;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.units.QuantityInfo;

/**
 * Defines methods for record creation. All arguments to methods in this interface should not be
 * <code>null</code>. Implementations should just create the relevant objects, not persist them.
 */
public interface IRecordFactory {


  StructuredDocument createStructuredDocument(String name, User createdBy, RSForm form);

  /**
   * Alternative for when importing content.
   *
   * @param name
   * @param createdBy
   * @param form
   * @param override
   */
  StructuredDocument createStructuredDocument(String name, User createdBy, final RSForm form,
      ImportOverride override);


  Folder createFolder(String name, User createdBy);

  Snippet createSnippet(String name, String content, User createdBy);

  /**
   * Creates a folder and sets it as system folder (i.e., isSystemFolder==true)
   *
   * @param name
   * @param createdBy
   * @return
   */
  Folder createSystemCreatedFolder(String name, User createdBy);

  /**
   * Standard method to create a new Notebook
   *
   * @param name
   * @param createdBy
   * @return
   */
  Notebook createNotebook(String name, User createdBy);

  /**
   * Create a notebook from an import source.
   *
   * @param name
   * @param createdBy
   * @param importOverride
   */
  Notebook createNotebook(String name, User createdBy, ImportOverride importOverride);

  /**
   * Create's a root folder for a user
   *
   * @param name
   * @param createdBy
   * @return
   */
  Folder createRootFolder(String name, User createdBy);

  RSForm createExperimentForm(String name, String desc, User createdBy);

  RSForm createNewForm();

  /**
   * Creates a BasicDocument form of type 'system'.
   *
   * @param owner the owner of the form
   * @return
   */
  RSForm createBasicDocumentForm(User owner);

  RSForm createFormForSeleniumTests(String tName, String desc, User createdBy);

  /**
   * Creates a folder that will be shared by a group
   *
   * @param grp
   * @param grpCreator
   * @return
   */
  Folder createCommunalGroupFolder(Group grp, User grpCreator);

  Folder createCommunalGroupSnippetFolder(Group grp, User createdBy);

  /**
   * Creates individual shared folder between two users.
   *
   * @param sharer
   * @param sharee
   * @return
   */
  Folder createIndividualSharedFolder(User sharer, User sharee);

  Folder createIndividualSharedSnippetsFolder(User sharer, User sharee);

  Folder createRootMediaFolder(User subject);

  /**
   * Creates an API inbox folder for the given user.
   *
   * @param subject
   * @return
   */
  Folder createApiInboxFolder(User subject);

  /**
   * Creates an Imports folder for the given user.
   *
   * @param subject
   * @return the Imports folder
   */
  Folder createImportsFolder(User subject);

  /**
   * Creates new Inventory Sample, with a default empty SubSample, owner and expiry date set.
   *
   * @param name           sample name
   * @param createdBy      creator and owner of the sample
   * @param sampleTemplate template to create sample from.
   */
  Sample createSample(String name, User createdBy, SampleTemplate sampleTemplate);

  /**
   * Creates new Inventory Sample, with no fields and a default empty SubSample.
   *
   * @param name      sample name
   * @param createdBy creator and owner of the sample
   */
  Sample createSample(String name, User createdBy);

  /**
   * Creates a new SubSample attached to the given {@link SampleEntity}
   * (a {@link Sample}, or a {@link SampleTemplate} when building a template's default subsample).
   *
   * @param name subsample name
   * @param createdBy creator and owner
   * @param sample owning sample or template
   */
  SubSample createSubSample(String name, User createdBy, SampleEntity sample);

  SampleTemplate createComplexSampleTemplate(String templateName, String desc, User createdBy);

  /**
   * Creates new Inventory Sample Template, with no fields and a default empty SubSample.
   *
   * @param name      template name
   * @param createdBy creator and owner of the template
   */
  SampleTemplate createSampleTemplate(String name, User createdBy);

  /**
   * Creates new Inventory instrument, with no fields
   *
   * @param name      instrument name
   * @param createdBy creator and owner of the instrument
   */
  Instrument createInstrument(String name, User createdBy, InstrumentTemplate instrumentTemplate);


  Container createListContainer(String name, User createdBy);

  Container createWorkbench(User owner);

  /**
   * Creates extra field of given type, assigned to provided inventory record.
   *
   * @param name      optional field name (if empty, default will be used)
   * @param type      currently supported are FieldType.STRING and NUMBER
   * @param createdBy creating user
   * @param invRec    sample/subsample
   */
  ExtraField createExtraField(String name, FieldType type, User createdBy, InventoryRecord invRec);

  /**
   * Creates extra field of given type. See also
   * {{@link #createExtraField(String, FieldType, User, InventoryRecord)}.
   *
   * @param type currently supported are FieldType.STRING and NUMBER
   */
  ExtraField createExtraField(FieldType type);

  ListOfMaterials createListOfMaterials(String name, Field field);

  MaterialUsage createMaterialUsage(ListOfMaterials parentLom, InventoryRecord invRec,
      QuantityInfo usedQuantity);

  Basket createBasket(String name, User owner);

  InventoryFile createInventoryFile(String fileName, FileProperty fileProperty, User createdBy);

  DigitalObjectIdentifier createDoiIdentifier(String identifier);

}
