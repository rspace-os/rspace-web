package com.researchspace.api.v1.service.impl;

import static com.researchspace.core.util.imageutils.ImageUtils.getBufferedImageFromUploadedFile;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.lang3.StringUtils.join;

import com.researchspace.api.v1.controller.FormTemplatesCommon.FormFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.FormPost;
import com.researchspace.api.v1.service.RSFormApiHandler;
import com.researchspace.model.User;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.AbstractFormManager;
import com.researchspace.service.FormManager;
import com.researchspace.service.IconImageManager;
import com.researchspace.session.UserSessionTracker;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

public class RSFormApiHandlerImpl implements RSFormApiHandler {

  private @Autowired FormManager formMgr;
  private @Autowired IconImageManager iconImageManager;
  private @Autowired IPermissionUtils permissionUtils;

  @Override
  public AbstractForm editForm(
      Long id, FormPost formPost, User user, UserSessionTracker activeUsers) {

    AbstractForm original = formMgr.get(id, user);

    AbstractForm tempForm = formMgr.getForEditing(id, user, activeUsers, false);
    Map<Long, Long> originalIdToTmpId = createOriginalToTmpIdMap(original, tempForm);

    // reject if the incoming fields have ids that do not exist in the original form.
    Set<Long> originalFormFieldIds = originalIdToTmpId.keySet();
    List<Long> incomingFormFieldIds = getIdsFromPost(formPost);
    assertValidIncomingFieldFormIds(id, originalFormFieldIds, incomingFormFieldIds);

    // validate can edit
    if (!tempForm.getEditStatus().isEditable()) {
      throw new IllegalStateException(
          String.format("Cannot edit this form: %s", tempForm.getEditStatus().name()));
    }

    List<Long> orderedTmpIds =
        createOrUpdateFields(formPost, user, tempForm, originalIdToTmpId, formMgr);

    doDeleteTempFields(user, incomingFormFieldIds, originalIdToTmpId, formMgr);
    AbstractForm reorderedForm = formMgr.reorderFields(tempForm.getId(), orderedTmpIds, user);
    // save - this relinquishes edit lock too, so just do it at the end.
    reorderedForm.setTags(formPost.getTags());
    reorderedForm.setName(formPost.getName());
    reorderedForm = doSave(reorderedForm, user);
    if (!original.isNewState()) {
      reorderedForm = formMgr.updateVersion(tempForm.getId(), user);
    }
    return reorderedForm;
  }

  AbstractForm doSave(AbstractForm form, User user) {
    return formMgr.save((RSForm) form, user);
  }

  // package scoped for testing
  void assertValidIncomingFieldFormIds(Long id, Set<Long> originalIds, List<Long> idsToKeep) {
    if (!CollectionUtils.isSubCollection(idsToKeep, originalIds)) {
      throw new IllegalArgumentException(
          String.format(
              "At least one  ID in the incoming form fields does not exist in the form with ID"
                  + " [%d]. Persisted fieldFormIds are: [%s], supplied are: [%s] ",
              id, join(originalIds, ","), join(idsToKeep, ",")));
    }
  }

  private List<Long> getIdsFromPost(FormPost formPost) {
    return formPost.getFields().stream()
        .map(FormFieldPost::getId)
        .filter(fid -> fid != null)
        .collect(Collectors.toList());
  }

  private Map<Long, Long> createOriginalToTmpIdMap(AbstractForm original, AbstractForm tempForm) {
    Map<Long, Long> originalIdToTmpId = new LinkedHashMap<>();
    for (int i = 0; i < original.getFieldForms().size(); i++) {
      originalIdToTmpId.put(
          original.getFieldForms().get(i).getId(), tempForm.getFieldForms().get(i).getId());
    }
    return originalIdToTmpId;
  }

  private List<Long> createOrUpdateFields(
      FormPost formPost,
      User user,
      AbstractForm tempForm,
      Map<Long, Long> originalIdToTmpId,
      AbstractFormManager<? extends AbstractForm> mgr) {
    List<Long> orderedIds = new ArrayList<Long>();
    for (FormFieldPost<? extends FieldForm> toPost : formPost.getFields()) {
      FieldForm savedFieldForm = null;
      if (toPost.getId() != null) {
        // we have an existing form
        savedFieldForm = mgr.updateFieldForm(toPost, originalIdToTmpId.get(toPost.getId()), user);
      } else {
        savedFieldForm = mgr.createFieldForm(toPost, tempForm.getId(), user);
      }
      orderedIds.add(savedFieldForm.getId());
    }
    return orderedIds;
  }

  private void doDeleteTempFields(
      User user,
      List<Long> idsToKeep,
      Map<Long, Long> originalIdToTmpId,
      AbstractFormManager<? extends AbstractForm> mgr) {
    Set<Long> originalIds = originalIdToTmpId.keySet();
    Collection<Long> originalIdsToDelete = CollectionUtils.removeAll(originalIds, idsToKeep);

    for (Long toDelete : originalIdsToDelete) {
      // remove fields we don't want
      mgr.deleteFieldFromForm(originalIdToTmpId.get(toDelete), user);
    }
  }

  public AbstractForm saveImage(MultipartFile file, Long formId, User user) throws IOException {
    RSForm form = formMgr.get(formId, user);
    if (!permissionUtils.isPermitted(form, PermissionType.WRITE, user)) {
      throw new AuthorizationException("Unauthorized attempt to update form icon");
    }
    Optional<BufferedImage> img =
        getBufferedImageFromUploadedFile(new SpringMultipartFileAdapter(file));
    if (!img.isPresent()) {
      throw new IllegalArgumentException(
          String.format("Couldn't parse file [%s] as an image.", file.getOriginalFilename()));
    }
    String suffix = getExtension(file.getOriginalFilename());
    IconEntity ice = IconEntity.createIconEntityFromImage(formId, img.get(), suffix);
    IconEntity iet = iconImageManager.saveIconEntity(ice, true);
    form.setIconId(iet.getId());
    return form;
  }
}
