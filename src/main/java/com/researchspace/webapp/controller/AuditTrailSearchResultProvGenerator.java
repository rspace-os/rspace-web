package com.researchspace.webapp.controller;

import static com.researchspace.core.util.DateUtil.convertDateToISOFormat;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import javax.xml.datatype.XMLGregorianCalendar;
import lombok.extern.slf4j.Slf4j;
import org.openprovenance.prov.interop.Formats.ProvFormat;
import org.openprovenance.prov.interop.InteropFramework;
import org.openprovenance.prov.model.Activity;
import org.openprovenance.prov.model.Agent;
import org.openprovenance.prov.model.Attribute;
import org.openprovenance.prov.model.Attribute.AttributeKind;
import org.openprovenance.prov.model.Document;
import org.openprovenance.prov.model.Entity;
import org.openprovenance.prov.model.Namespace;
import org.openprovenance.prov.model.ProvFactory;
import org.openprovenance.prov.model.QualifiedName;
import org.openprovenance.prov.model.Used;
import org.openprovenance.prov.model.WasAssociatedWith;
import org.openprovenance.prov.model.WasAttributedTo;
import org.openprovenance.prov.model.WasDerivedFrom;
import org.openprovenance.prov.model.WasGeneratedBy;
import org.openprovenance.prov.model.WasInvalidatedBy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Slf4j
public class AuditTrailSearchResultProvGenerator {
  public static final String ATTACHMENT_FILENAME_RSPACE_AUDIT_TRAIL_PROV =
      "attachment; filename=\"rspace-audit-prov.json\"";
  private ProvFactory provFactory = InteropFramework.getDefaultFactory();
  private static final String RS = "rs";
  private static final String RS_USER = "rs-user";
  private static final String RS_RESOURCE = "rs-resource";
  private static final String DCT = "dcterms";
  private static final String FOAF = "foaf";
  private static final String OWL = "owl";

  @Value("${server.urls.prefix}")
  private String rspaceBase;

  void setRspaceBase(String rspaceBase) {
    this.rspaceBase = rspaceBase;
  }

  private InteropFramework interopF = new InteropFramework(provFactory);

  ResponseEntity<String> convertToProv(ISearchResults<AuditTrailSearchResult> res) {
    Namespace ns = createNamespace();
    ByteArrayOutputStream os = new ByteArrayOutputStream(10000);
    List<AuditTrailSearchResult> auditEntries = res.getResults();
    Map<String, Agent> agents = new HashMap<>();
    Map<String, Entity> entities = new HashMap<>();
    Map<String, List<Entity>> versions = new HashMap<>();
    List<WasDerivedFrom> derivations = new ArrayList<>();
    List<Activity> activities = new ArrayList<>();
    List<WasAssociatedWith> associations = new ArrayList<>();
    List<WasAttributedTo> attributions = new ArrayList<>();
    List<WasGeneratedBy> generations = new ArrayList<>();
    List<Used> uses = new ArrayList<>();
    List<WasInvalidatedBy> invalidations = new ArrayList<>();
    QualifiedName xsdString = ns.qualifiedName("xsd", "string", provFactory);
    QualifiedName dcTitleQn = ns.qualifiedName(DCT, "title", provFactory);
    QualifiedName foafName = ns.qualifiedName(FOAF, "name", provFactory);
    for (AuditTrailSearchResult auditEntry : auditEntries) {
      String subject = auditEntry.getEvent().getSubject();
      String fullName = auditEntry.getEvent().getFullName();
      QualifiedName agentQn = ns.qualifiedName(RS_USER, subject, provFactory);
      Attribute agentFullName = provFactory.newAttribute(foafName, fullName, xsdString);
      Agent agent = provFactory.newAgent(agentQn, List.of(agentFullName));
      agents.putIfAbsent(subject, agent);
      XMLGregorianCalendar timestamp =
          provFactory.newISOTime(
              convertDateToISOFormat(auditEntry.getTimestamp(), TimeZone.getDefault()));
      QualifiedName activityQn = ns.qualifiedName(RS, UUID.randomUUID().toString(), provFactory);
      AuditAction action = auditEntry.getEvent().getAction();
      Activity activity =
          provFactory.newActivity(
              activityQn,
              timestamp,
              timestamp,
              List.of(provFactory.newAttribute(AttributeKind.PROV_TYPE, action, xsdString)));
      QualifiedName association = ns.qualifiedName(RS, UUID.randomUUID().toString(), provFactory);
      associations.add(provFactory.newWasAssociatedWith(association, activityQn, agentQn));
      activities.add(activity);
      if (auditEntry.getData() != null
          && auditEntry.getData().getData() != null
          && auditEntry.getData().getData().getData() != null) {
        Map<String, Object> data = auditEntry.getData().getData().getData();
        String resourceId = data.getOrDefault("id", "n/a").toString();
        String name = data.getOrDefault("name", "n/a").toString();
        if (!name.equals("n/a")) {
          Attribute dctTitle =
              provFactory.newAttribute(
                  dcTitleQn, name, ns.qualifiedName("xsd", "string", provFactory));
          QualifiedName resourceQn = ns.qualifiedName(RS_RESOURCE, resourceId, provFactory);
          Entity resource = provFactory.newEntity(resourceQn, List.of());
          entities.putIfAbsent(resourceId, resource);
          QualifiedName attribution =
              ns.qualifiedName(RS, UUID.randomUUID().toString(), provFactory);
          attributions.add(provFactory.newWasAttributedTo(attribution, resourceQn, agentQn));
          switch (action) {
            case CREATE:
              QualifiedName generation =
                  ns.qualifiedName(RS, UUID.randomUUID().toString(), provFactory);
              generations.add(
                  provFactory.newWasGeneratedBy(
                      generation, resourceQn, activityQn, timestamp, null));
              QualifiedName firstVersionQn =
                  ns.qualifiedName(RS_RESOURCE, resourceId + "v1", provFactory);
              versions.putIfAbsent(
                  resourceId,
                  new ArrayList<>(
                      List.of(provFactory.newEntity(firstVersionQn, List.of(dctTitle)))));
              QualifiedName creation =
                  ns.qualifiedName(RS, UUID.randomUUID().toString(), provFactory);
              attributions.add(provFactory.newWasAttributedTo(creation, firstVersionQn, agentQn));
              break;
            case DELETE:
              QualifiedName invalidation =
                  ns.qualifiedName(RS, UUID.randomUUID().toString(), provFactory);
              invalidations.add(
                  provFactory.newWasInvalidatedBy(
                      invalidation, resourceQn, activityQn, timestamp, null));
              break;
            case WRITE:
            case RENAME:
              var previousVersions = versions.get(resourceId);
              int versionNumber = previousVersions.size() + 1;
              Entity latest = previousVersions.get(versionNumber - 2);
              QualifiedName newVersionQn =
                  ns.qualifiedName(RS_RESOURCE, resourceId + "v" + versionNumber, provFactory);
              previousVersions.add(provFactory.newEntity(newVersionQn, List.of(dctTitle)));
              WasDerivedFrom derivation =
                  provFactory.newWasDerivedFrom(
                      ns.qualifiedName(RS, UUID.randomUUID().toString(), provFactory),
                      newVersionQn,
                      latest.getId());
              derivation.setActivity(activityQn);
              derivations.add(derivation);
              QualifiedName edition =
                  ns.qualifiedName(RS, UUID.randomUUID().toString(), provFactory);
              attributions.add(provFactory.newWasAttributedTo(edition, newVersionQn, agentQn));
              break;
            // TODO MOVE
            default:
              QualifiedName used = ns.qualifiedName(RS, UUID.randomUUID().toString(), provFactory);
              uses.add(provFactory.newUsed(used, activityQn, resourceQn, timestamp));
              break;
          }
        }
      }
    }
    for (Entity genericEntity : entities.values()) {
      if (!versions.containsKey(genericEntity.getId().getLocalPart())) continue;
      var myVersions = versions.get(genericEntity.getId().getLocalPart());
      Entity latest = myVersions.get(myVersions.size() - 1);
      var others = latest.getOther();
      genericEntity.getOther().add(others.get(0));
      genericEntity
          .getOther()
          .add(
              provFactory.newOther(
                  ns.qualifiedName(OWL, "sameAs", provFactory),
                  latest.getId(),
                  ns.qualifiedName("xsd", "string", provFactory)));
      others.add(
          provFactory.newOther(
              ns.qualifiedName(OWL, "sameAs", provFactory),
              genericEntity.getId(),
              ns.qualifiedName("xsd", "string", provFactory)));
    }
    Document document = provFactory.newDocument();
    document.setNamespace(ns);
    document.getStatementOrBundle().addAll(agents.values());
    document.getStatementOrBundle().addAll(entities.values());
    document.getStatementOrBundle().addAll(activities);
    document.getStatementOrBundle().addAll(associations);
    document.getStatementOrBundle().addAll(attributions);
    document.getStatementOrBundle().addAll(generations);
    document.getStatementOrBundle().addAll(uses);
    document.getStatementOrBundle().addAll(invalidations);
    document.getStatementOrBundle().addAll(derivations);
    versions.values().stream().forEach(document.getStatementOrBundle()::addAll);
    interopF.writeDocument(os, ProvFormat.JSON, document);
    return createProvEntityResponse(os.toString());
  }

  private Namespace createNamespace() {
    Namespace ns =
        new Namespace(
            new Hashtable<String, String>(
                Map.of(
                    RS,
                    rspaceBase,
                    RS_USER,
                    rspaceBase + "user/",
                    RS_RESOURCE,
                    rspaceBase + "globalId/",
                    DCT,
                    "http://purl.org/dc/terms/",
                    FOAF,
                    "http://xmlns.com/foaf/0.1/",
                    OWL,
                    "http://www.w3.org/2002/07/owl#")));
    ns.addKnownNamespaces();
    return ns;
  }

  private ResponseEntity<String> createProvEntityResponse(String prov) {
    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.setContentType(MediaType.parseMediaType("application/json"));
    responseHeaders.add("Content-Disposition", ATTACHMENT_FILENAME_RSPACE_AUDIT_TRAIL_PROV);
    ResponseEntity<String> rc = new ResponseEntity<>(prov, responseHeaders, HttpStatus.OK);
    return rc;
  }
}
