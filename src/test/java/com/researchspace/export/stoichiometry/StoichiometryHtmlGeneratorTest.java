package com.researchspace.export.stoichiometry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.service.StoichiometryService;
import com.researchspace.testutils.RSpaceTestUtils;
import java.util.List;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Integration test for {@link StoichiometryHtmlGenerator} that exercises the real Velocity engine
 * with the production {@code stoichiometry-table.vm} template. Verifies header generation for
 * reaction-less stoichiometries (RSDEV-1091, commit 905507a4) and for the existing reaction-linked
 * paths.
 */
public class StoichiometryHtmlGeneratorTest {

  private static final String REACTIONLESS_HEADER = " this reactionless stoichiometry.";
  private static final String DEFAULT_REACTION_HEADER = " the chemical reaction above.";

  private StoichiometryHtmlGenerator generator;
  private StoichiometryService stoichiometryService;
  private User exporter;

  @BeforeEach
  public void setUp() {
    VelocityEngine velocityEngine =
        RSpaceTestUtils.setupVelocity("src/main/resources/velocityTemplates");
    stoichiometryService = Mockito.mock(StoichiometryService.class);
    generator = new StoichiometryHtmlGenerator();
    ReflectionTestUtils.setField(generator, "velocityEngine", velocityEngine);
    ReflectionTestUtils.setField(generator, "stoichiometryService", stoichiometryService);
    ReflectionTestUtils.setField(generator, "urlPrefix", "https://test.researchspace.com");

    exporter = new User();
    exporter.setUsername("exporter");

    when(stoichiometryService.getById(eq(1L), any(), any()))
        .thenReturn(stoichiometryDtoWithMolecule());
  }

  @Test
  public void addStoichiometryLinks_reactionlessStoichiometry_rendersReactionlessHeader() {
    String html =
        "<html><body>"
            + "<div data-stoichiometry-table-only=\"true\" "
            + "data-stoichiometry-table=\"{&quot;id&quot;:1,&quot;revision&quot;:null}\"></div>"
            + "</body></html>";

    String result = generator.addStoichiometryLinks(html, exporter);

    assertTrue(
        result.contains("Stoichiometry Information for" + REACTIONLESS_HEADER),
        () -> "expected reaction-less header but got:\n" + result);
    assertFalse(
        result.contains(DEFAULT_REACTION_HEADER),
        "default reaction-linked header must not appear when"
            + " data-stoichiometry-table-only=\"true\"");
  }

  @Test
  public void addStoichiometryLinks_reactionLinkedWithAltText_rendersAltAsHeader() {
    String alt = " my custom reaction context.";
    String html =
        "<html><body>"
            + "<img alt=\""
            + alt
            + "\" "
            + "data-stoichiometry-table=\"{&quot;id&quot;:1,&quot;revision&quot;:null}\">"
            + "</body></html>";

    String result = generator.addStoichiometryLinks(html, exporter);

    assertTrue(
        result.contains("Stoichiometry Information for" + alt),
        () -> "expected alt-derived header but got:\n" + result);
    assertFalse(
        result.contains(REACTIONLESS_HEADER),
        "reaction-less header must not appear when data-stoichiometry-table-only is absent");
  }

  @Test
  public void addStoichiometryLinks_reactionLinkedWithoutAltText_rendersDefaultHeader() {
    String html =
        "<html><body>"
            + "<img data-stoichiometry-table=\"{&quot;id&quot;:1,&quot;revision&quot;:null}\">"
            + "</body></html>";

    String result = generator.addStoichiometryLinks(html, exporter);

    assertTrue(
        result.contains("Stoichiometry Information for" + DEFAULT_REACTION_HEADER),
        () -> "expected default reaction-linked header but got:\n" + result);
    assertFalse(
        result.contains(REACTIONLESS_HEADER),
        "reaction-less header must not appear when data-stoichiometry-table-only is absent");
  }

  private StoichiometryDTO stoichiometryDtoWithMolecule() {
    StoichiometryMoleculeDTO mol =
        StoichiometryMoleculeDTO.builder()
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .molecularWeight(46.07)
            .coefficient(1.0)
            .build();
    return StoichiometryDTO.builder().id(1L).revision(null).molecules(List.of(mol)).build();
  }
}
