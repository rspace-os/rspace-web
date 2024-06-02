package com.axiope.model.record.init;

/** Interface for SampleTemplateBuiltIns */
public interface SampleTemplateBuiltIn extends IBuiltinContent {

  /**
   * Returns an optional string, possibly null, of a preview image to display by this template, e.g.
   * 'bacteriaSample150.png'. The associated image should be in src/main/resources/formIcons
   *
   * @return
   */
  String getPreviewImageName();
}
