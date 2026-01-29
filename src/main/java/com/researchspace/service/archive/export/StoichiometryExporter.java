package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.linkedelements.FieldElementLinkPairs;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.service.archive.export.stoichiometry.LinkableStoichiometry;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import lombok.SneakyThrows;

/** Exports ExternalWorkFlow data associated with an RSpace Field in a Structured Doc. */
public class StoichiometryExporter extends AbstractFieldExporter<LinkableStoichiometry> {

  private final User exporter;
  private final StoichiometryReader reader;

  public StoichiometryExporter(
      FieldExporterSupport support, StoichiometryReader reader, User exporter) {
    super(support);
    this.exporter = exporter;
    this.reader = reader;
  }

  @SneakyThrows
  public void addStoichiometriesAndExport(
      FieldExportContext fieldExportContext, String htmlContent) {
    FieldElementLinkPairs<LinkableStoichiometry> stoichiometryDataLinks =
        new FieldElementLinkPairs<>(LinkableStoichiometry.class);
    List<StoichiometryDTO> extractedStoichiometries =
        reader.extractStoichiometriesFromFieldContents(htmlContent);
    for (StoichiometryDTO extracted : extractedStoichiometries) {
      // ignore revision in case the rtfData is not uptodate with changes made via the API
      StoichiometryDTO stoichiometryDTO =
          support.getStoichiometryService().getById(extracted.getId(), null, exporter);
      // for now, remove inventory links because we do not export inventory data
      stoichiometryDTO.getMolecules().forEach(m -> m.setInventoryLink(null));
      stoichiometryDataLinks.add(
          new FieldElementLinkPair<>(new LinkableStoichiometry(stoichiometryDTO), ""));
    }
    for (FieldElementLinkPair<LinkableStoichiometry> linkableStoichiometryPair :
        stoichiometryDataLinks.getPairs()) {
      fieldExportContext
          .getExportRecordList()
          .getStoichiometries()
          .add(linkableStoichiometryPair.getElement().getStoichiometry());
      export(fieldExportContext, linkableStoichiometryPair);
    }
  }

  @Override
  void createFieldArchiveObject(
      LinkableStoichiometry item, String archiveLink, FieldExportContext context) {
    ArchivalField archiveField = context.getArchiveField();
    archiveField.addStoichiometry(item.getStoichiometry());
  }

  /**
   * We do not add Stoichiometry to the html of the field Therefore this just returns the original
   * html of the field unmodified
   */
  @Override
  String doUpdateLinkText(
      FieldElementLinkPair<LinkableStoichiometry> itemPair,
      String replacementUrl,
      FieldExportContext context) {
    return context.getArchiveField().getFieldData();
  }

  /**
   * The abstract factory being used expects this method to also copy any required resources to the
   * archive and the other implementing classes do this with EcatMedia files.
   *
   * <p>For stoichiometry data the chem files are already being copied from the Gallery so this
   * method can return empty string
   */
  @Override
  String getReplacementUrl(FieldExportContext context, LinkableStoichiometry item)
      throws URISyntaxException, IOException {
    return "";
  }
}
