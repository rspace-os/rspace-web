package com.researchspace.service.inventory.csvexport;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CsvContentToExport {
  private String listsOfMaterials = "";
  private String containers = "";
  private String samples = "";
  private String subSamples = "";
  private String sampleTemplates = "";

  public String getCombinedContent() {
    return Stream.of(listsOfMaterials, containers, samples, subSamples, sampleTemplates)
        .filter(Objects::nonNull)
        .filter(Predicate.not(String::isBlank))
        .collect(Collectors.joining("\n"));
  }

  public Map<String, String> getNamesAndContentMap() {
    Map<String, String> result = new LinkedHashMap<>();
    result.put("list_of_materials", listsOfMaterials);
    result.put("containers", containers);
    result.put("samples", samples);
    result.put("subSamples", subSamples);
    result.put("sample_templates", sampleTemplates);
    result.values().removeIf(String::isBlank);
    return result;
  }
}
