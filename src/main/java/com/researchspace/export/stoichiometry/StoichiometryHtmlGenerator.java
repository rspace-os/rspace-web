package com.researchspace.export.stoichiometry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.service.StoichiometryService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.ui.velocity.VelocityEngineUtils;

@Service
public class StoichiometryHtmlGenerator {
  @Value("${server.urls.prefix}")
  private String urlPrefix;

  @Autowired private StoichiometryService stoichiometryService;
  @Autowired private VelocityEngine velocityEngine;

  @SneakyThrows
  public String addStoichiometryLinks(String html, User exporter) {
    StoichiometryTableData.serverPrefix = urlPrefix;
    Document doc = Jsoup.parse(html);
    Elements stoichiometryElements = doc.getElementsByAttribute("data-stoichiometry-table");
    for (Element stoichiometryElement : stoichiometryElements) {
      String stoichiometryAttribute = stoichiometryElement.attr("data-stoichiometry-table");
      StoichiometryDTO extracted =
          new ObjectMapper().readValue(stoichiometryAttribute, StoichiometryDTO.class);
      StoichiometryDTO stoichiometryDTO =
          stoichiometryService.getById(extracted.getId(), extracted.getRevision(), exporter);
      List<StoichiometryTableData> molecules = new ArrayList<>();
      for (StoichiometryMoleculeDTO moleculeDTO : stoichiometryDTO.getMolecules()) {
        molecules.add(new StoichiometryTableData(moleculeDTO));
      }
      Map<String, Object> context = new HashMap<>();
      context.put("molecules", molecules);
      String header = stoichiometryElement.attr("alt");
      if (header.isEmpty()) {
        header = " the chemical reaction above.";
      }
      context.put("header", header);
      String stoichiometryTableHtml =
          VelocityEngineUtils.mergeTemplateIntoString(
              velocityEngine, "pdf/stoichiometry-table.vm", "UTF-8", context);
      Element testForLinkToChem = extractLinkToChemistryIfExistsOrNull(stoichiometryElement);
      if (testForLinkToChem != null) {
        testForLinkToChem.after(stoichiometryTableHtml);
      } else {
        stoichiometryElement.after(stoichiometryTableHtml);
      }
    }
    return doc.html();
  }

  private Element extractLinkToChemistryIfExistsOrNull(Element stoichiometryElement) {
    Element first = stoichiometryElement.nextElementSibling();
    if (first == null) {
      return null;
    }
    if (first.nextElementSibling() == null) {
      return null;
    }
    Element second = first.nextElementSibling();
    if (second.tagName() == null) {
      return null;
    }
    Elements links = second.select("a[href]");
    if (links != null && links.size() > 0) {
      Element linkEl = links.get(0);
      if (linkEl.html().contains("cdxml")) {
        return second;
      }
    }
    return null;
  }
}
