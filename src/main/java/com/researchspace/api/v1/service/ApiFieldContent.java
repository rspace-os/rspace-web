package com.researchspace.api.v1.service;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.field.Field;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Holder for HTML content and linked media files. */
@Data
@AllArgsConstructor
public class ApiFieldContent {

  private String content;
  private Field field;
  private List<EcatMediaFile> mediaFiles = new ArrayList<>();
}
