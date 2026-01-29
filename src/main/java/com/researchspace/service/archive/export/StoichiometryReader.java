package com.researchspace.service.archive.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.service.archive.StoichiometryImporter.IdAndRevision;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class StoichiometryReader {

  @SneakyThrows
  public List<StoichiometryDTO> extractStoichiometriesFromFieldContents(String htmlContent) {
    List<StoichiometryDTO> extractedStoichiometries = new ArrayList<>();
    Document doc = Jsoup.parse(htmlContent);
    Elements stoichiometryElements = doc.getElementsByAttribute("data-stoichiometry-table");
    for (Element stoichiometryElement : stoichiometryElements) {
      String stoichiometryAttribute = stoichiometryElement.attr("data-stoichiometry-table");
      StoichiometryDTO extracted =
          new ObjectMapper().readValue(stoichiometryAttribute, StoichiometryDTO.class);
      extractedStoichiometries.add(extracted);
    }
    return extractedStoichiometries;
  }

  @SneakyThrows
  public String createReplacementHtmlContentForTargetStoichiometryInFieldData(
      String htmlContent, StoichiometryDTO target, IdAndRevision newStoichiometry) {
    Document doc = Jsoup.parse(htmlContent);
    Elements stoichiometryElements = doc.getElementsByAttribute("data-stoichiometry-table");
    for (Element stoichiometryElement : stoichiometryElements) {
      String stoichiometryAttribute = stoichiometryElement.attr("data-stoichiometry-table");
      ObjectMapper mapper = new ObjectMapper();
      StoichiometryDTO extracted = mapper.readValue(stoichiometryAttribute, StoichiometryDTO.class);
      if (Objects.equals(extracted.getId(), target.getId())
          && Objects.equals(extracted.getRevision(), target.getRevision())) {
        stoichiometryElement.attr(
            "data-stoichiometry-table", mapper.writeValueAsString(newStoichiometry));
      }
    }
    return doc.body().html();
  }
}
