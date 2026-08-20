package com.researchspace.model;


import com.researchspace.model.core.UniquelyIdentifiable;


/**
 * Interface that all entity objects that can be linked to in text fields should
 * implement. Currently this is just a marker interface to ensure type safety,
 * but methods can be added in future if need be. Any new class that implements
 * this interface needs to be integrated with various other classes and
 * features:
 * <ul>
 * <li>A Velocity template should be added in the 'textFieldElements' folder.
 * <li>TemplateController (from rspace-web) should return this template as a Mustache
 * template (for use in the UI)
 * <li>FieldParser, FieldContents and RichTextUpdater (from rspace-web)
 * should be updated to provide sample Strings for tests and for parsing the
 * stringfield representation back to the underlying object.
 * <li>BaseRecordAdapter (from rspace-web) should provide a means to return the containing
 * BaseRecord, for permissions checking.
 * <li>Export (PDF, HTML and XML) and import from XML code should be adapted to
 * export and import the linked objects
 * <li>RealTransactionSpringTestBase#createComplexDocument test setup (from rspace-web)
 * method should be updated to provide a test document containing the new links.
 * <li>An audit table will be required for loading older revisions correctly.
 * <li>If the object can be edited independently of a field, you may need to add
 * 'revision={num}' to URLs loading the resource, for dealing with revision
 * numbers, and also add a 'data-rsrevision' attribute. (There are methods in
 * RichTextUpdater to help with this).
 * <li> Update DocumentCopyManager to handle the copying policy of the new element.
 * </ul>
 */
public interface IFieldLinkableElement extends UniquelyIdentifiable {

}
