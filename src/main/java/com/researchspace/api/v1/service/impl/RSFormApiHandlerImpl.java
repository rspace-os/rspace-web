package com.researchspace.api.v1.service.impl;

import static com.researchspace.core.util.imageutils.ImageUtils.getBufferedImageFromUploadedFile;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.researchspace.api.v1.controller.FormTemplatesCommon.FormFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.FormPost;
import com.researchspace.api.v1.service.RSFormApiHandler;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FormFieldSaveItem;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.FormDefinitionCommand;
import com.researchspace.service.FormManager;
import com.researchspace.service.IconImageManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.session.UserSessionTracker;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

public class RSFormApiHandlerImpl implements RSFormApiHandler {

  private @Autowired FormManager formMgr;
  private @Autowired IconImageManager iconImageManager;
  private @Autowired IPermissionUtils permissionUtils;
  private @Autowired MessageSourceUtils messages;

  @Override
  public AbstractForm createForm(FormPost formPost, User user) {
    return formMgr.createForm(toDefinition(formPost), user);
  }

  @Override
  public AbstractForm editForm(
      Long id, FormPost formPost, User user, UserSessionTracker activeUsers) {
    return formMgr.editForm(id, toDefinition(formPost), user, activeUsers);
  }

  /**
   * Translates an API form into the form manager's command. Each API field is the field's settings
   * source: copied onto a saved field, it sets only the properties the API carries, so the rest
   * (for example mandatory, or a radio field's alphabetical sort) are kept.
   */
  FormDefinitionCommand toDefinition(FormPost formPost) {
    List<FormFieldSaveItem> fields = new ArrayList<>();
    for (FormFieldPost<? extends FieldForm> field : formPost.getFields()) {
      if (field == null) {
        fields.add(null); // reported as a field error by the form manager
        continue;
      }
      FormFieldSaveItem item =
          new FormFieldSaveItem(
              field.getId(),
              field.getType(),
              field.getFieldKey(),
              field.getDisplayRule(),
              field,
              !field.isDisplayRuleSet());
      fields.add(item);
    }
    return new FormDefinitionCommand(
        formPost.getName(), formPost.getTags(), formPost.getFormType(), fields);
  }

  public AbstractForm saveImage(MultipartFile file, Long formId, User user) throws IOException {
    RSForm form = formMgr.get(formId, user);
    if (!permissionUtils.isPermitted(form, PermissionType.WRITE, user)) {
      throw new AuthorizationException(messages.getMessage("errors.authorization.formIconUpdate"));
    }
    Optional<BufferedImage> img =
        getBufferedImageFromUploadedFile(new SpringMultipartFileAdapter(file));
    if (!img.isPresent()) {
      throw new IllegalArgumentException(
          messages.getMessage(
              "errors.inventory.icon.imageParseFailure",
              new Object[] {file.getOriginalFilename()}));
    }
    String suffix = getExtension(file.getOriginalFilename());
    IconEntity ice = IconEntity.createIconEntityFromImage(formId, img.get(), suffix);
    IconEntity iet = iconImageManager.saveIconEntity(ice, true);
    form.setIconId(iet.getId());
    return form;
  }
}
