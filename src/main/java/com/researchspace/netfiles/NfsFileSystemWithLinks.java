package com.researchspace.netfiles;

import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;

@Slf4j
@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class NfsFileSystemWithLinks extends NfsFileSystemInfo {

  private static final long serialVersionUID = -199095108889443303L;

  private List<NfsElement> foundNfsLinks = new ArrayList<>();
  private transient List<NfsResourceDetails> checkedNfsLinks = new ArrayList<>();

  // map of error messages, indexed by absolute paths
  private Map<String, String> checkedNfsLinkMessages = new HashMap<>();

  public NfsFileSystemWithLinks(NfsFileSystemInfo nfsFileSystem) {
    try {
      PropertyUtils.copyProperties(this, nfsFileSystem);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      log.warn("couldn't initialise NfsFileSystemWithLinks object from " + nfsFileSystem, e);
    }
  }
}
