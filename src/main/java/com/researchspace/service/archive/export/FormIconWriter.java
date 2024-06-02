package com.researchspace.service.archive.export;

import com.researchspace.dao.IconImgDao;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.RSForm;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * If document has a form with an icon id, writes this to the record's export folder with file name
 * 'formIcon_{formId}.{imageType}'
 */
public class FormIconWriter {

  private @Autowired IconImgDao imgIconDao;
  static final String FILE_PREFIX = "formIcon";

  /** Regex */
  public static final String FORM_ICON_REGEX = "formIcon_\\d+\\.\\w+";

  /** Regex template for String formatting with an id */
  public static final String FORM_ICON_REGEX_TEMPLATE = "formIcon_%d\\.\\w+";

  public void writeFormIconEntityFile(RSForm form, File recordFolder) throws IOException {
    if (form.getIconId() > 0) {
      IconEntity formIcon = imgIconDao.getIconEntity(form.getIconId());
      if (formIcon != null) {
        Long formId = form.getId();
        String formIconName = getFormIconFileName(formIcon, formId);
        File iconFile = new File(recordFolder, formIconName);
        try (FileOutputStream fos = new FileOutputStream(iconFile)) {
          IOUtils.write(formIcon.getIconImage(), fos);
        }
      }
    }
  }

  public String getFormIconFileName(IconEntity formIcon, Long formId) {
    return String.format("%s_%d.%s", FILE_PREFIX, formId, formIcon.getImgType());
  }
}
