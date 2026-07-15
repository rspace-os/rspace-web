package com.researchspace.service.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.researchspace.model.User;
import java.time.Year;
import java.util.List;
import lombok.SneakyThrows;
import lombok.Value;
import org.springframework.stereotype.Component;

/**
 * Composes a DataCite-style metadata sidecar in the LRZ {@code ltds-datacite4.3} YAML convention,
 * where every scalar is wrapped as {@code {value: ...}}. The only sidecar format for now; a format
 * seam can be reintroduced when a second format (e.g. RO-Crate) is added.
 */
@Component
public class DataCiteYamlSidecarGenerator {

  private static final String SCHEMA_VERSION = "ltds-datacite4.3";

  private final YAMLMapper yamlMapper =
      (YAMLMapper) new YAMLMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

  @SneakyThrows
  public GeneratedSidecar generate(SidecarGenerationContext ctx) {
    DataCiteMetadata model =
        new DataCiteMetadata(
            new Valued<>(SCHEMA_VERSION),
            new Types(new Valued<>("Dataset"), new Valued<>("")),
            List.of(creatorFor(ctx)),
            new Publisher(new Valued<>(ctx.getInstitutionName())),
            new Valued<>(String.valueOf(Year.now().getValue())),
            relatedItemsFor(ctx));
    String content = yamlMapper.writeValueAsString(model);
    return new GeneratedSidecar(filenameFor(ctx), content);
  }

  private Creator creatorFor(SidecarGenerationContext ctx) {
    User user = ctx.getUser();
    return new Creator(
        new Valued<>("Personal"),
        new Valued<>(user.getFullNameSurnameFirst()),
        new Valued<>(user.getFirstName()),
        new Valued<>(user.getLastName()),
        orcidIdentifiers(ctx.getOrcidId()),
        List.of(new Affiliation(new Valued<>(ctx.getInstitutionName()))));
  }

  /** ORCID as a DataCite nameIdentifier, or null (omitted) when the user has none. */
  private List<NameIdentifier> orcidIdentifiers(String orcidId) {
    if (orcidId == null || orcidId.isBlank()) {
      return null;
    }
    return List.of(
        new NameIdentifier(
            new Valued<>(orcidId), new Valued<>("https://orcid.org"), new Valued<>("ORCID")));
  }

  /** One {@code relatedItem} per S3 object, or null (omitted) when the folder is empty. */
  private List<RelatedItem> relatedItemsFor(SidecarGenerationContext ctx) {
    if (ctx.getFiles() == null || ctx.getFiles().isEmpty()) {
      return null;
    }
    return ctx.getFiles().stream().map(f -> relatedItem(ctx.getBucketName(), f)).toList();
  }

  private RelatedItem relatedItem(String bucket, SidecarFileEntry file) {
    return new RelatedItem(
        new Valued<>("Dataset"),
        new Valued<>("HasPart"),
        new S3Location(bucket, file.key(), file.sizeBytes(), file.etag(), file.storageClass()));
  }

  /** {@code <folder leaf>.sidecar.yaml}, falling back to the bucket name at the root. */
  private String filenameFor(SidecarGenerationContext ctx) {
    String trimmed = ctx.getFolderPath() == null ? "" : ctx.getFolderPath().replaceAll("/+$", "");
    int slash = trimmed.lastIndexOf('/');
    String leaf = slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
    if (leaf.isEmpty()) {
      leaf = ctx.getBucketName();
    }
    return leaf + ".sidecar.yaml";
  }

  // ---- serialization model (ltds-datacite4.3 {value:} convention) ----

  /** Wraps a scalar as {@code {value: X}}, the LRZ convention. */
  @Value
  static class Valued<T> {
    T value;
  }

  @Value
  @JsonPropertyOrder({
    "schemaVersion",
    "types",
    "creators",
    "publisher",
    "publicationYear",
    "relatedItems"
  })
  static class DataCiteMetadata {
    Valued<String> schemaVersion;
    Types types;
    List<Creator> creators;
    Publisher publisher;
    Valued<String> publicationYear;
    List<RelatedItem> relatedItems;
  }

  @Value
  @JsonPropertyOrder({"resourceTypeGeneral", "resourceType"})
  static class Types {
    Valued<String> resourceTypeGeneral;
    Valued<String> resourceType;
  }

  @Value
  @JsonPropertyOrder({
    "nameType",
    "name",
    "givenName",
    "familyName",
    "nameIdentifiers",
    "affiliations"
  })
  static class Creator {
    Valued<String> nameType;
    Valued<String> name;
    Valued<String> givenName;
    Valued<String> familyName;
    List<NameIdentifier> nameIdentifiers;
    List<Affiliation> affiliations;
  }

  @Value
  @JsonPropertyOrder({"nameIdentifier", "schemeURI", "nameIdentifierScheme"})
  static class NameIdentifier {
    Valued<String> nameIdentifier;
    Valued<String> schemeURI;
    Valued<String> nameIdentifierScheme;
  }

  @Value
  static class Affiliation {
    Valued<String> name;
  }

  @Value
  static class Publisher {
    Valued<String> name;
  }

  @Value
  @JsonPropertyOrder({"relatedItemType", "relationType", "s3Location"})
  static class RelatedItem {
    Valued<String> relatedItemType;
    Valued<String> relationType;
    S3Location s3Location;
  }

  /**
   * S3 object reference; fields are plain (not {@code {value:}}-wrapped) per the LRZ convention.
   */
  @Value
  @JsonPropertyOrder({"bucket", "key", "sizeBytes", "etag", "storageClass"})
  static class S3Location {
    String bucket;
    String key;
    Long sizeBytes;
    String etag;
    String storageClass;
  }
}
