package com.researchspace.webapp.controller;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.core.util.MediaUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.velocity.VelocityEngineUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Returns a Mustache template that has been generated from a Velocity template. */
@Controller("fieldTemplates")
@RequestMapping("/fieldTemplates")
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.FOREVER)
public class TemplateController extends BaseController {

  protected static final String INSERTED_DOCUMENT_TEMPLATE = "insertedDocumentTemplate";

  protected static final String INSERTED_MISCDOC_TEMPLATE = "insertedMiscdocTemplate";

  protected static final String INSERTED_EXTERNAL_DOCUMENT_TEMPLATE =
      "insertedExternalDocumentTemplate";

  protected static final String INSERTED_BOX_VERSION_TEMPLATE = "insertedBoxVersionTemplate";

  protected static final String INSERTED_AV_TEMPLATE = "avTableForTinymceTemplate";

  protected static final String INSERTED_RAW_IMAGE_TEMPLATE = "insertedImageTemplate";

  protected static final String INSERTED_COMMENT_TEMPLATE = "commentLink";

  protected static final String INSERTED_LINK_TEMPLATE = "linkedRecordLink";

  protected static final String INSERTED_SKETCH_TEMPLATE = "sketchLink";

  protected static final String INSERTED_ANNOTATED_IMAGE_TEMPLATE = "annotatedImageLink";

  protected static final String INSERTED_CHEMELEMENT_TEMPLATE = "chemElementLink";

  protected static final String NET_FILESTORE_LINK_TEMPLATE = "netFilestoreLink";

  protected static final String EQUATION_TEMPLATE = "equationLink";

  protected static final String CALCULATION_TABLE_TEMPLATE = "calcTableLink";

  private Map<String, String> templateLookUp = new ConcurrentHashMap<String, String>();

  @Autowired private VelocityEngine velocity;

  @IgnoreInLoggingInterceptor(ignoreAll = true)
  @ResponseBody
  @GetMapping("/ajax/{name}")
  public String getTemplate(@PathVariable("name") String name) {
    if (templateLookUp.containsKey(name)) {
      return templateLookUp.get(name);
    }
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    mustachify(velocityModel, "id", "name");

    String templateFile;
    if (isRawImage(name)) {
      mustachify(
          velocityModel,
          "fieldId",
          "itemId",
          "name",
          "width",
          "height",
          "rotation",
          "milliseconds");
      templateFile = "rawImageLink.vm";
    } else if (isAttachment(name)) {
      mustachify(velocityModel, "iconPath");
      velocityModel.put("infoIconSrc", "/images/getInfo12.png");
      if (isMiscDoc(name)) {
        velocityModel.put("datatype", MediaUtils.MISC_MEDIA_FLDER_NAME);
      } else {
        velocityModel.put("datatype", MediaUtils.DOCUMENT_MEDIA_FLDER_NAME);
      }
      templateFile = "attachmentLink.vm";
    } else if (isExternalDoc(name)) {
      mustachify(velocityModel, "iconPath", "recordURL", "fileStore", "badgeIconPath");
      templateFile = "externalDocumentLink.vm";
    } else if (isBoxVersionedDoc(name)) {
      mustachify(
          velocityModel,
          "recordURL",
          "description",
          "owner",
          "iconPath",
          "versionID",
          "versionNumber",
          "sha1",
          "size",
          "createdAt");
      templateFile = "boxVersionedLink.vm";
    } else if (isAV(name)) {
      mustachify(velocityModel, "iconSrc", "extension", "filename", "imgClass", "compositeId");
      velocityModel.put("videoHTML", "{{{videoHTML}}}");
      velocityModel.put("infoIconSrc", "/images/getInfo12.png");
      templateFile = "mediaPlayerHTML.vm";
    } else if (isComment(name)) {
      mustachify(velocityModel, "imageURL");
      velocityModel.put("cssClass", FieldParserConstants.COMMENT_CLASS_NAME);
      templateFile = getVelTemplateName(INSERTED_COMMENT_TEMPLATE);
    } else if (isLink(name)) {
      mustachify(velocityModel, "globalId");
      templateFile = getVelTemplateName(INSERTED_LINK_TEMPLATE);
    } else if (isSketch(name)) {
      velocityModel.put("cssClass", FieldParserConstants.SKETCH_IMG_CLASSNAME);
      mustachify(velocityModel, "height", "width", "unused");
      templateFile = getVelTemplateName(INSERTED_SKETCH_TEMPLATE);
    } else if (isAnnotatedImage(name)) {
      velocityModel.put("type", FieldParserConstants.DATA_TYPE_ANNOTATION);
      mustachify(velocityModel, "annotationId", "height", "width", "unused");
      templateFile = getVelTemplateName(INSERTED_ANNOTATED_IMAGE_TEMPLATE);
    } else if (isChemElement(name)) {
      velocityModel.put("cssClass", FieldParserConstants.CHEM_IMG_CLASSNAME);
      mustachify(
          velocityModel,
          "ecatChemFileId",
          "fileName",
          "sourceParentId",
          "width",
          "height",
          "fullwidth",
          "fullheight",
          "tstamp");
      templateFile = getVelTemplateName(INSERTED_CHEMELEMENT_TEMPLATE);
    } else if (name.equals(EQUATION_TEMPLATE)) {
      mustachify(velocityModel, "id", "equation", "svgWidth", "svgHeight");
      templateFile = getVelTemplateName(EQUATION_TEMPLATE);
    } else if (name.equals(CALCULATION_TABLE_TEMPLATE)) {
      mustachify(velocityModel, "id", "data");
      velocityModel.put("tableHtml", "{{{tableHtml}}}");
      templateFile = getVelTemplateName(CALCULATION_TABLE_TEMPLATE);
    } else if (name.equals(NET_FILESTORE_LINK_TEMPLATE)) {
      mustachify(
          velocityModel, "name", "linktype", "fileStoreId", "relFilePath", "nfsId", "nfsType");
      templateFile = "nfsLink.vm";
    } else {
      return null;
    }

    String rc = processTemplate(velocityModel, templateFile);
    templateLookUp.put(name, rc);
    return rc;
  }

  private String getVelTemplateName(String requested) {
    return requested + ".vm";
  }

  // replace Velocity syntax with Mustache syntax
  private void mustachify(Map<String, Object> velocityModel, String... properties) {
    for (String property : properties) {
      velocityModel.put(property, "{{" + property + "}}");
    }
  }

  private String processTemplate(Map<String, Object> velocityModel, String templateName) {
    velocityModel.put("src", "src");
    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(velocity, templateName, "UTF-8", velocityModel);
    return msg;
  }

  private boolean isSketch(String name) {
    return name.equals(INSERTED_SKETCH_TEMPLATE);
  }

  private boolean isAnnotatedImage(String name) {
    return name.equals(INSERTED_ANNOTATED_IMAGE_TEMPLATE);
  }

  private boolean isLink(String name) {
    return name.equals(INSERTED_LINK_TEMPLATE);
  }

  private boolean isComment(String name) {
    return name.equals(INSERTED_COMMENT_TEMPLATE);
  }

  private boolean isRawImage(String name) {
    return name.equals(INSERTED_RAW_IMAGE_TEMPLATE);
  }

  private boolean isAV(String name) {
    return name.equals(INSERTED_AV_TEMPLATE);
  }

  private boolean isAttachment(String name) {
    return isDoc(name) || isMiscDoc(name);
  }

  private boolean isMiscDoc(String name) {
    return name.equals(INSERTED_MISCDOC_TEMPLATE);
  }

  private boolean isDoc(String name) {
    return name.equals(INSERTED_DOCUMENT_TEMPLATE);
  }

  private boolean isChemElement(String name) {
    return name.equals(INSERTED_CHEMELEMENT_TEMPLATE);
  }

  private boolean isExternalDoc(String name) {
    return name.equals(INSERTED_EXTERNAL_DOCUMENT_TEMPLATE);
  }

  private boolean isBoxVersionedDoc(String name) {
    return name.equals(INSERTED_BOX_VERSION_TEMPLATE);
  }
}
