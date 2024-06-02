package com.researchspace.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.researchspace.dao.EcatImageAnnotationDao;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.Thumbnail.SourceType;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.service.EcatImageAnnotationManager;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service("ecatImageAnnotationManager")
public class EcatImageAnnotationManagerImpl implements EcatImageAnnotationManager {

  Logger logger = LoggerFactory.getLogger(EcatImageAnnotationManager.class);
  @Autowired private EcatImageAnnotationDao ecatImageAnnotationDao;

  @Autowired private BaseRecordAdaptable recordAdapter;

  @Autowired private ApplicationEventPublisher publisher;

  @Autowired private IPermissionUtils permUtils;

  @Override
  public EcatImageAnnotation get(long id, User user) {
    EcatImageAnnotation annot = ecatImageAnnotationDao.get(id);
    return annot; // init for hibernate
  }

  @Override
  public EcatImageAnnotation save(EcatImageAnnotation ecatImageAnnotation, User user) {

    EcatImageAnnotation annotation = ecatImageAnnotationDao.save(ecatImageAnnotation);

    // Whether creating a new annotation or editing an existing one,
    // we want to clear all thumbnails for our image id and parent id.
    publisher.publishEvent(
        new ThumbnailSourceUpdateEvent(
            this, SourceType.IMAGE, annotation.getImageId(), annotation.getParentId()));

    return annotation;
  }

  @Override
  public void delete(long id, User user) throws Exception {
    EcatImageAnnotation annotation = ecatImageAnnotationDao.get(id);

    Long sourceId = null;
    Long parentId = null;
    if (annotation != null) {
      sourceId = annotation.getImageId();
      parentId = annotation.getParentId();
    }

    ecatImageAnnotationDao.remove(id);

    if (sourceId != null) {
      publisher.publishEvent(
          new ThumbnailSourceUpdateEvent(this, SourceType.IMAGE, sourceId, parentId));
    }
  }

  @Override
  public EcatImageAnnotation getByParentIdAndImageId(long parentId, long imageId, User user) {
    if (user == null) {
      logger.warn("user is null, it should be non-null for permissions.");
    }
    EcatImageAnnotation ann = ecatImageAnnotationDao.getFromParentIdAndImageId(parentId, imageId);
    if (ann != null) {
      Optional<BaseRecord> br = recordAdapter.getAsBaseRecord(ann);
      if (!br.isPresent()
          || (user != null && !permUtils.isPermitted(br.get(), PermissionType.READ, user))) {
        throw new AuthorizationException(
            "Unauthorised attempt to access annotation "
                + ann.getId()
                + " by "
                + user.getUsername());
      }
    }
    return ann;
  }

  @Override
  public String removeBackgroundImageNodesFromZwibblerAnnotation(String annotations) {

    if (StringUtils.isEmpty(annotations)) {
      return annotations;
    }

    // annotations starts with "zwibbler3." prefix
    String zwibblerPrefix = "zwibbler3.";

    if (!annotations.startsWith(zwibblerPrefix)) {
      throw new IllegalArgumentException("annotation string doesn't start with " + zwibblerPrefix);
    }
    ObjectMapper mapper = new ObjectMapper();
    String jsonPart = annotations.substring(zwibblerPrefix.length());
    ArrayNode jsonArray;
    try {
      jsonArray = (ArrayNode) mapper.readTree(jsonPart);
    } catch (IOException e) {
      logger.warn("Error while reading json: ", e);
      throw new IllegalArgumentException("annotation string is not a json array");
    }

    Iterator<JsonNode> iterator = jsonArray.iterator();
    while (iterator.hasNext()) {
      JsonNode parsedNode = iterator.next();
      if (isBackgroundImageNode(parsedNode)) {
        iterator.remove();
      }
    }

    String result = zwibblerPrefix + jsonArray;
    return result;
  }

  private boolean isBackgroundImageNode(JsonNode jsonNode) {
    String nodeType = jsonNode.path("type").asText("");
    boolean locked = jsonNode.path("locked").asBoolean(false);
    return "ImageNode".equals(nodeType) && Boolean.TRUE.equals(locked);
  }
}
