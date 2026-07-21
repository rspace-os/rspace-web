package com.researchspace.service.impl;

import static com.researchspace.core.util.DateUtil.convertDateToISOFormat;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.service.ProvManager;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import javax.xml.datatype.XMLGregorianCalendar;
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
import org.springframework.stereotype.Service;

@Service
public class ProvManagerImpl implements ProvManager {
  private ProvFactory provFactory = InteropFramework.getDefaultFactory();

  @Value("${server.urls.prefix}")
  private String rspaceBase;

  void setRspaceBase(String rspaceBase) {
    this.rspaceBase = rspaceBase;
  }

  public ProvFactory getProvFactory() {
    return provFactory;
  }

  @Override
  public Document createDocument(ISearchResults<AuditTrailSearchResult> res) {
    Namespace ns = createNamespace();
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
              QualifiedName derivationQn =
                  ns.qualifiedName(RS, UUID.randomUUID().toString(), provFactory);
              WasDerivedFrom derivation =
                  provFactory.newWasDerivedFrom(derivationQn, newVersionQn, latest.getId());
              derivation.setActivity(activityQn);
              derivations.add(derivation);
              QualifiedName edition =
                  ns.qualifiedName(RS, UUID.randomUUID().toString(), provFactory);
              attributions.add(provFactory.newWasAttributedTo(edition, newVersionQn, agentQn));
              break;
            default: // DOWNLOAD, DUPLICATE, EXPORT, MOVE, READ, RESTORE, SEARCH, SHARE, SIGN,
              // TRANSFER, UNSHARE, VIEW, WITNESSED
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
    return document;
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
}
