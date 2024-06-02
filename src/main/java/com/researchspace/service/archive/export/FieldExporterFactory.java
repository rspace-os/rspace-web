package com.researchspace.service.archive.export;

import com.researchspace.model.IFieldLinkableElement;

/** Generic factory for creating FieldExporters dynamically */
class FieldExporterFactory<F extends AbstractFieldExporter<T>, T extends IFieldLinkableElement> {

  Class<F> exporterClass;
  Class<T> elementClass;

  FieldExporterFactory(Class<F> exporterClass, Class<T> elementClass) {
    super();
    this.exporterClass = exporterClass;
    this.elementClass = elementClass;
  }

  /*
   * Static Factory method to create the factory
   */
  static <F extends AbstractFieldExporter<T>, T extends IFieldLinkableElement>
      FieldExporterFactory<F, T> createFactory(
          final Class<F> exporterClass, final Class<T> elementClass) {
    return new FieldExporterFactory<F, T>(exporterClass, elementClass);
  }

  /*
   * Creates the FieldExporter instance.
   */
  public F create(FieldExporterSupport support)
      throws InstantiationException, IllegalAccessException {
    F handler = exporterClass.newInstance();
    handler.setSupport(support);
    return handler;
  }
}
