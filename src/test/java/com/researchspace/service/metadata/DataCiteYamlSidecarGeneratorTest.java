package com.researchspace.service.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.researchspace.model.User;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataCiteYamlSidecarGeneratorTest {

  private final DataCiteYamlSidecarGenerator generator = new DataCiteYamlSidecarGenerator();
  private final YAMLMapper yaml = new YAMLMapper();

  private User user() {
    User u = new User("jmuller");
    u.setFirstName("Jana");
    u.setLastName("Müller");
    return u;
  }

  @Test
  void generatesLtdsDataCiteYamlWithCurrentUserAsCreator() throws Exception {
    SidecarGenerationContext ctx =
        SidecarGenerationContext.builder()
            .user(user())
            .institutionName("Leibniz Supercomputing Centre")
            .bucketName("lrz-rs-experiments")
            .folderPath("XRD-Experiments/")
            .files(List.of())
            .build();

    GeneratedSidecar result = generator.generate(ctx);

    assertTrue(
        result.getFilename().endsWith(".sidecar.yaml"),
        "sidecar filename should end with .sidecar.yaml, was: " + result.getFilename());

    JsonNode root = yaml.readTree(result.getContent());
    assertEquals("ltds-datacite4.3", root.path("schemaVersion").path("value").asText());

    JsonNode creator = root.path("creators").path(0);
    assertEquals("Müller, Jana", creator.path("name").path("value").asText());
    assertEquals("Personal", creator.path("nameType").path("value").asText());
  }

  @Test
  void populatesTypesPublisherAndYearFromInstanceConfig() throws Exception {
    SidecarGenerationContext ctx =
        SidecarGenerationContext.builder()
            .user(user())
            .institutionName("Leibniz Supercomputing Centre")
            .bucketName("lrz-rs-experiments")
            .folderPath("XRD-Experiments/")
            .files(List.of())
            .build();

    JsonNode root = yaml.readTree(generator.generate(ctx).getContent());

    assertEquals("Dataset", root.path("types").path("resourceTypeGeneral").path("value").asText());
    assertEquals("", root.path("types").path("resourceType").path("value").asText());
    assertEquals(
        "Leibniz Supercomputing Centre",
        root.path("publisher").path("name").path("value").asText());
    assertEquals(
        String.valueOf(Year.now().getValue()), root.path("publicationYear").path("value").asText());
  }

  @Test
  void creatorCarriesGivenFamilyNameAndInstanceAffiliation() throws Exception {
    SidecarGenerationContext ctx =
        SidecarGenerationContext.builder()
            .user(user())
            .institutionName("Leibniz Supercomputing Centre")
            .bucketName("lrz-rs-experiments")
            .folderPath("XRD-Experiments/")
            .files(List.of())
            .build();

    JsonNode creator = yaml.readTree(generator.generate(ctx).getContent()).path("creators").path(0);

    assertEquals("Jana", creator.path("givenName").path("value").asText());
    assertEquals("Müller", creator.path("familyName").path("value").asText());
    assertEquals(
        "Leibniz Supercomputing Centre",
        creator.path("affiliations").path(0).path("name").path("value").asText());
  }

  @Test
  void includesOrcidNameIdentifierWhenUserHasOne() throws Exception {
    SidecarGenerationContext ctx =
        SidecarGenerationContext.builder()
            .user(user())
            .orcidId("https://orcid.org/0000-0002-1825-0097")
            .institutionName("Leibniz Supercomputing Centre")
            .bucketName("lrz-rs-experiments")
            .folderPath("XRD-Experiments/")
            .files(List.of())
            .build();

    JsonNode nameId =
        yaml.readTree(generator.generate(ctx).getContent())
            .path("creators")
            .path(0)
            .path("nameIdentifiers")
            .path(0);

    assertEquals(
        "https://orcid.org/0000-0002-1825-0097",
        nameId.path("nameIdentifier").path("value").asText());
    assertEquals("https://orcid.org", nameId.path("schemeURI").path("value").asText());
    assertEquals("ORCID", nameId.path("nameIdentifierScheme").path("value").asText());
  }

  @Test
  void omitsNameIdentifiersWhenUserHasNoOrcid() throws Exception {
    SidecarGenerationContext ctx =
        SidecarGenerationContext.builder()
            .user(user())
            .institutionName("Leibniz Supercomputing Centre")
            .bucketName("lrz-rs-experiments")
            .folderPath("XRD-Experiments/")
            .files(List.of())
            .build();

    JsonNode creator = yaml.readTree(generator.generate(ctx).getContent()).path("creators").path(0);

    assertTrue(
        creator.path("nameIdentifiers").isMissingNode(),
        "nameIdentifiers must be omitted entirely when the user has no ORCID");
  }

  @Test
  void mapsEachS3FileToRelatedItemWithS3Location() throws Exception {
    SidecarGenerationContext ctx =
        SidecarGenerationContext.builder()
            .user(user())
            .institutionName("Leibniz Supercomputing Centre")
            .bucketName("lrz-rs-experiments")
            .folderPath("XRD-Experiments/")
            .files(
                List.of(
                    new SidecarFileEntry(
                        "XRD-Experiments/xrd_run_041.dat", 2202009L, "\"b2c3d4\"", "STANDARD"),
                    new SidecarFileEntry(
                        "XRD-Experiments/run_log.log", 45056L, "\"e5f678\"", "GLACIER")))
            .build();

    JsonNode related = yaml.readTree(generator.generate(ctx).getContent()).path("relatedItems");

    assertEquals(2, related.size());
    JsonNode first = related.path(0);
    assertEquals("Dataset", first.path("relatedItemType").path("value").asText());
    assertEquals("HasPart", first.path("relationType").path("value").asText());
    JsonNode loc = first.path("s3Location");
    assertEquals("lrz-rs-experiments", loc.path("bucket").asText());
    assertEquals("XRD-Experiments/xrd_run_041.dat", loc.path("key").asText());
    assertEquals(2202009L, loc.path("sizeBytes").asLong());
    assertEquals("\"b2c3d4\"", loc.path("etag").asText());
    assertEquals("STANDARD", loc.path("storageClass").asText());
  }
}
