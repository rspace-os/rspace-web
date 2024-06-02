package com.researchspace.linkedelements;

import com.researchspace.core.util.NumberUtils;
import com.researchspace.model.netfiles.NfsElement;
import java.util.Optional;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;

@Slf4j
public class NfsConverter extends AbstractFieldElementConverter implements FieldElementConverter {

  // <a class="nfs_file mceNonEditable" data-linktype="file|directory" href="#"
  // rel="62:/readme.txt">

  @Override
  public Optional<NfsElement> jsoup2LinkableElement(FieldContents contents, Element el) {
    Optional<NfsElement> rc = Optional.empty();
    String link = el.attr("rel");
    if (!StringUtils.isBlank(link)) {
      Matcher m = NfsElement.ExpectedLinkFormat.matcher(link);
      if (m.matches()) {
        String[] parts = link.split(":");
        long fileStoreId = NumberUtils.stringToInt(parts[0], -1);
        String relPath = parts[1];
        if (fileStoreId > 0) {
          NfsElement nfs = new NfsElement(fileStoreId, relPath);
          nfs.setLinkType(el.attr("data-linktype"));
          if (isLong(el.attr("data-nfsid"))) {
            nfs.setNfsId(Long.valueOf(el.attr("data-nfsid")));
          }
          rc = Optional.of(nfs);
        }
      }
    }
    if (rc.isPresent()) {
      contents.addElement(rc.get(), link, NfsElement.class);
    } else {
      log.warn("NfsElement with rel attribute [{}] not found", link);
    }
    return rc;
  }

  private boolean isLong(String nfsId) {
    try {
      Long.valueOf(nfsId);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
