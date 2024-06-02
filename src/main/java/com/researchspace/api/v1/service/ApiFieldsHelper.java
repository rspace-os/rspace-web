package com.researchspace.api.v1.service;

import com.researchspace.api.v1.model.ApiDocumentField;
import com.researchspace.api.v1.model.ApiField;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.ValidatingField;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.RSChemElementManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** To deal with API fields conversion when fields are sent between RSpace server and API client. */
@Component
@Slf4j
public class ApiFieldsHelper {

  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  public static final Pattern incomingAttachmentPattern =
      Pattern.compile("<(?:fileId|docId)=(\\d+)>");

  public static final String commaReplacement = "&#44;";

  private @Autowired IPermissionUtils permUtils;

  private @Autowired RichTextUpdater richTextUpdater;

  private @Autowired BaseRecordManager baseMgr;

  private @Autowired RSChemElementManager rsChemElementManager;

  /**
   * Converts incoming content of API field so it's ready to be saved in DB.
   *
   * <p>Various changes may be applied, e.g. for text fields the file attachment markers are
   * replaced with html fragments.
   *
   * @returns converted apiFieldContents
   */
  public ApiFieldContent getContentToSaveForIncomingApiContent(
      String apiContent, Field field, User user) {

    if (FieldType.TEXT.equals(field.getType())) {
      return replaceNewAttachmentMarkersWithHtmlFragments(apiContent, field, user);
    } else if (FieldType.CHOICE.equals(field.getType())) {
      ApiFieldContent rc = convertChoiceFieldContentReceivedFromClient(apiContent);
      rc.setField(field);
      return rc;
    } else {
      return new ApiFieldContent(apiContent, field, Collections.emptyList());
    }
  }

  /**
   * Scans the api field content and replaces new attachment with appropriate html fragments.
   *
   * @param field
   * @param user
   */
  private ApiFieldContent replaceNewAttachmentMarkersWithHtmlFragments(
      String apiFieldContent, Field field, User user) {

    if (StringUtils.isEmpty(apiFieldContent)) {
      return new ApiFieldContent(apiFieldContent, field, Collections.emptyList());
    }

    String result = apiFieldContent;
    Matcher matcher = incomingAttachmentPattern.matcher(apiFieldContent);
    List<EcatMediaFile> medias = new ArrayList<>();
    Map<String, BaseRecord> attachmentMarkerFragmentsAndLinkedItems = new HashMap();
    while (matcher.find()) {
      String attachmentMarkerFragment = matcher.group(0);
      Long fileId = Long.parseLong(matcher.group(1));
      BaseRecord linkedItem = baseMgr.get(fileId, user);
      if (linkedItem == null) {
        throw new IllegalStateException("couldn't find media file for fileId " + fileId);
      }
      if (!permUtils.isRecordAccessPermitted(user, linkedItem, PermissionType.READ)) {
        SECURITY_LOG.warn(
            "Unauthorised API call by user {} to access resource {}",
            user.getUsername(),
            linkedItem.getId());
      }
      attachmentMarkerFragmentsAndLinkedItems.put(attachmentMarkerFragment, linkedItem);
      if (linkedItem.isMediaRecord()) {
        medias.add((EcatMediaFile) linkedItem);
      }
    }
    if (attachmentMarkerFragmentsAndLinkedItems.size() > 0) {
      String processed =
          callApiFieldsParallelHelper(attachmentMarkerFragmentsAndLinkedItems, user, field, result);
      result = processed;
    }
    return new ApiFieldContent(result, field, medias);
  }

  /**
   * If this is a chemistry field, sequential calls to chemical server take 1-2 seconds each:
   * therefore we parallelise the helper calls.
   */
  private String callApiFieldsParallelHelper(
      Map<String, BaseRecord> attachmentMarkerFragmentsAndLinkedItems,
      User user,
      Field field,
      String result) {
    AtomicReference<String> documentString = new AtomicReference<>(result);
    attachmentMarkerFragmentsAndLinkedItems.keySet().stream()
        .parallel()
        .forEach(
            attachmentMarkerFragment -> {
              BaseRecord linkedItem =
                  attachmentMarkerFragmentsAndLinkedItems.get(attachmentMarkerFragment);
              String replacement = getHtmlFragmentForMediaFile(linkedItem, field.getId(), user);
              UnaryOperator<String> updateFunction =
                  s -> s.replace(attachmentMarkerFragment, Matcher.quoteReplacement(replacement));
              documentString.getAndUpdate(updateFunction);
            });
    return documentString.get();
  }

  private String getHtmlFragmentForMediaFile(BaseRecord linkedItem, Long fieldId, User user) {
    String htmlFragment = null;
    if (linkedItem instanceof EcatImage) {
      EcatImage img = (EcatImage) linkedItem;
      htmlFragment = richTextUpdater.generateRawImageElement(img, fieldId + "");
    } else if (linkedItem instanceof EcatAudio) {
      EcatAudio audio = (EcatAudio) linkedItem;
      htmlFragment = richTextUpdater.generateURLString(audio, fieldId);
    } else if (linkedItem instanceof EcatVideo) {
      EcatVideo video = (EcatVideo) linkedItem;
      htmlFragment = richTextUpdater.generateURLString(video, fieldId);
    } else if (linkedItem instanceof EcatDocumentFile) {
      EcatDocumentFile doc = (EcatDocumentFile) linkedItem;
      htmlFragment = richTextUpdater.generateURLString(doc);
    } else if (linkedItem instanceof EcatChemistryFile) {
      EcatChemistryFile chemistryFile = (EcatChemistryFile) linkedItem;
      RSChemElement chemElement = null;
      try {
        chemElement =
            rsChemElementManager.generateRsChemElementFromChemistryFile(
                chemistryFile, fieldId, user);
        htmlFragment = richTextUpdater.generateURLString(chemistryFile, chemElement, fieldId);
      } catch (Exception e) {
        log.error("Unable to generate RSChemElement from chemistry file.", e);
        htmlFragment =
            richTextUpdater.generateURLStringForEcatChemistryFileAfterError(chemistryFile);
      }
    } else if (linkedItem.isFolder() || linkedItem.isStructuredDocument()) {
      htmlFragment = richTextUpdater.generateURLStringForInternalLink(linkedItem);
    }
    return htmlFragment;
  }

  private ApiFieldContent convertChoiceFieldContentReceivedFromClient(String incomingChoices) {
    String content = incomingChoices;
    if (StringUtils.isNotEmpty(incomingChoices)) {
      String[] choices = incomingChoices.split(",");
      StringBuilder sb = new StringBuilder();
      for (String selectedChoice : choices) {
        if (sb.length() > 0) {
          sb.append("&");
        }
        String choiceToSave = selectedChoice.replace(commaReplacement, ",");
        sb.append("fieldChoices=").append(choiceToSave);
      }
      content = sb.toString();
    }
    return new ApiFieldContent(content, null, Collections.emptyList());
  }

  /**
   * Converts outgoing content of API fields so it's easier to handle by API client.
   *
   * <p>Various changes may be applied, e.g. for choice fields we strip the
   * fieldChoices/fieldSelectedChoices bits leaving just comma-separated values.
   */
  public void updateOutgoingApiFieldsContent(List<ApiDocumentField> apiFields, User user) {
    if (apiFields == null) {
      return;
    }
    for (ApiDocumentField field : apiFields) {
      if (ApiField.ApiFieldType.CHOICE.equals(field.getType())) {
        field.setContent(convertChoiceFieldContentSentToClient(field.getContent()));
      }
    }
  }

  private String convertChoiceFieldContentSentToClient(String choiceFieldContent) {
    if (StringUtils.isNotEmpty(choiceFieldContent)) {
      String[] choices = choiceFieldContent.split("&");
      StringBuilder sb = new StringBuilder();
      for (String selectedChoice : choices) {
        if (sb.length() > 0) {
          sb.append(",");
        }
        String choice = "";
        if (selectedChoice.startsWith("fieldChoices=")) {
          choice = selectedChoice.substring("fieldChoices=".length());
        } else if (selectedChoice.startsWith("fieldSelectedChoices=")) {
          choice = selectedChoice.substring("fieldSelectedChoices=".length());
        }
        String choiceToSend = choice.replace(",", commaReplacement);
        sb.append(choiceToSend);
      }
      return sb.toString();
    }
    return choiceFieldContent;
  }

  private ErrorList validateIncomingApiFieldContent(
      String incomingContent, ValidatingField formField) {
    String toValidate = incomingContent;
    if (FieldType.CHOICE.equals(formField.getType())) {
      toValidate = convertChoiceFieldContentReceivedFromClient(incomingContent).getContent();
    }
    return formField.validate(toValidate);
  }

  /**
   * Validates fields against template/form but handles validation error messages using an
   * errorConsumer (rather than the default throwing of IAE)
   *
   * @param apiFields
   * @param formFields
   * @param user
   * @param errorConsumer
   */
  public void checkApiFieldsMatchingFormFields(
      List<? extends ApiField> apiFields,
      List<? extends ValidatingField> formFields,
      User user,
      Consumer<String> errorConsumer) {

    boolean prevalidatedOk = preValidate(apiFields, formFields, errorConsumer);
    if (prevalidatedOk) {
      validateApiFieldsAgainstFormFields(apiFields, formFields, user, errorConsumer);
    }
  }

  /**
   * @returns true if fields are prevalidating fine
   */
  private boolean preValidate(
      List<?> apiFields, List<?> formFields, Consumer<String> errorConsumer) {
    if (apiFields.isEmpty()) {
      return true; // no fields provided, so fields won't be changed
    }

    int providedFieldsNumber = apiFields.size();
    int formFieldsNumber = formFields.size();
    if (providedFieldsNumber != formFieldsNumber) {
      // if new fields are provided, there must be exactly one for each form field
      errorConsumer.accept(
          "\"fields\" array should have "
              + formFieldsNumber
              + " fields, but had "
              + providedFieldsNumber);
      return false;
    }
    return true;
  }

  void consumeErrorMsgAndThrowIAE(String message) {
    if (!StringUtils.isEmpty(message)) {
      throw new IllegalArgumentException(message);
    }
  }

  /**
   * Checks if the provided apiFields are matching formFields, or throws IllegalArgumentException if
   * there is a problem.
   *
   * @param user
   */
  public void checkApiFieldsMatchingFormFields(
      List<? extends ApiField> apiFields, List<FieldForm> formFields, User user) {
    checkApiFieldsMatchingFormFields(apiFields, formFields, user, this::consumeErrorMsgAndThrowIAE);
  }

  /**
   * Ensures the type and content of each apiField matches type and validation of each formField
   *
   * @param errorConsumer
   */
  private void validateApiFieldsAgainstFormFields(
      List<? extends ApiField> apiFields,
      List<? extends ValidatingField> formFields,
      User user,
      Consumer<String> errorConsumer) {
    if (apiFields.isEmpty()) {
      return;
    }
    for (int i = 0; i < formFields.size(); i++) {
      ApiField apiField = apiFields.get(i);
      ValidatingField formField = formFields.get(i);

      String apiFieldContent = apiField.getContent();
      if (apiFieldContent == null) {
        continue; // didn't provide content for the field, so safe to skip
      }

      String apiFieldType = apiField.getType() != null ? apiField.getType().toString() : null;
      String formFieldType = formField.getType().toString().toLowerCase();
      if (apiFieldType != null && !formFieldType.equals(apiFieldType)) {
        errorConsumer.accept(
            "Mismatched field type in "
                + getFieldDescForErrorMsg(apiField, i)
                + ": \""
                + formFieldType
                + "\" type expected, but was \""
                + apiFieldType
                + "\"");
      }

      String apiFieldName = apiField.getName();
      String formFieldName = formField.getName();
      if (StringUtils.isNotEmpty(apiFieldName) && !formFieldName.equals(apiFieldName)) {
        errorConsumer.accept(
            "Mismatched field name in "
                + getFieldDescForErrorMsg(apiField, i)
                + ": \""
                + formFieldName
                + "\" name expected, but was \""
                + apiFieldName
                + "\"");
      }

      ErrorList validate = validateIncomingApiFieldContent(apiFieldContent, formField);
      if (validate.hasErrorMessages()) {
        errorConsumer.accept(
            "Validation problem with "
                + getFieldDescForErrorMsg(apiField, i)
                + ": "
                + validate.getAllErrorMessagesAsStringsSeparatedBy(","));
      }

      // check attachments in text fields
      if ("text".equals(formFieldType)) {
        String attachmentError = checkPermissionsToTextFieldAttachments(apiFieldContent, user);
        if (attachmentError != null) {
          errorConsumer.accept(
              "Attachment with fileId="
                  + attachmentError
                  + " from "
                  + getFieldDescForErrorMsg(apiField, i)
                  + " cannot be accessed");
        }
      }
    }
  }

  @Value("${api.beta.allowUnauthorizedLinks:false}")
  private boolean apiBetaAllowUnauthorizedLinks;

  /**
   * Check if there is a problem with accessing any of the new attachments (<fileId=123> pattern) in
   * provided content
   *
   * @return problematic attachment id, or null if no problems
   */
  private String checkPermissionsToTextFieldAttachments(String apiFieldContent, User user) {
    if (StringUtils.isNotEmpty(apiFieldContent)) {
      Matcher matcher = incomingAttachmentPattern.matcher(apiFieldContent);
      while (matcher.find()) {
        Long fileId = Long.parseLong(matcher.group(1));
        try {
          BaseRecord linkedItem = baseMgr.get(fileId, user);
          if (linkedItem.isMediaRecord() && ((EcatMediaFile) linkedItem).isAV()) {
            throw new IllegalArgumentException(
                "Linking to audio/video attachments is not supported "
                    + "in current version of RSpace API (fileId "
                    + fileId
                    + ")");
          }
          if (!apiBetaAllowUnauthorizedLinks
              && !permUtils.isRecordAccessPermitted(user, linkedItem, PermissionType.READ)) {
            SECURITY_LOG.warn(
                "Unauthorised API call by user {} to access resource {}",
                user.getUsername(),
                linkedItem.getId());
            return "" + fileId;
          }
        } catch (DataAccessException e) {
          return "" + fileId;
        }
      }
    }
    return null;
  }

  private String getFieldDescForErrorMsg(ApiField apiField, int fieldIndex) {
    if (apiField != null && apiField.getId() != null) {
      return "field with id: " + apiField.getId();
    }
    if (apiField != null && StringUtils.isNotBlank(apiField.getName())) {
      return "field " + (fieldIndex + 1) + " \"" + apiField.getName() + "\"";
    }
    return "field " + (fieldIndex + 1);
  }
}
