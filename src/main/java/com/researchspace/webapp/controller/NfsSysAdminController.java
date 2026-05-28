package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.NfsManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for managing nfs file systems (for external file stores).
 *
 * <p>Sysadmin should be able to use it even if netfilestores.enabled=false (to create/test
 * configuration before activating it for users).
 */
@Controller
@RequestMapping("/system/netfilesystem")
public class NfsSysAdminController extends BaseController {

  @Autowired private NfsManager nfsManager;

  public void setNfsManager(NfsManager nfsManager) {
    this.nfsManager = nfsManager;
  }

  /**
   * Returns netfilesystem page fragment (from JSP). Doesn't set any model properties.
   *
   * @return maintenance page view
   */
  @GetMapping("/ajax/view")
  public ModelAndView getFileSystemsView(Model model) {
    assertIsSysAdmin();
    return new ModelAndView("system/netfilesystem_ajax");
  }

  private void assertIsSysAdmin() {
    User subject = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(subject);
  }

  /**
   * Returns list of configured NfsFileSystems, ordered by id.
   *
   * @return
   */
  @GetMapping("/ajax/list")
  @ResponseBody
  public List<NfsFileSystem> getFileSystemsList() {
    assertIsSysAdmin();
    return nfsManager.getFileSystems();
  }

  @PostMapping("/save")
  @ResponseBody
  public NfsFileSystemSaveResult saveFileSystem(@RequestBody NfsFileSystem nfsFileSystem) {
    assertIsSysAdmin();

    List<String> unknownReaders = findUnknownUsernames(nfsFileSystem.getReadWhitelist());
    List<String> unknownWriters = findUnknownUsernames(nfsFileSystem.getWriteWhitelist());

    nfsManager.saveNfsFileSystem(nfsFileSystem);
    return new NfsFileSystemSaveResult(nfsFileSystem.getId(), unknownReaders, unknownWriters);
  }

  /**
   * Returns the subset of whitelisted usernames that do not correspond to any RSpace user. The
   * 'everyone' sentinel ({@code *}) is ignored. The list is preserved as typed so that sysadmins
   * can pre-provision access for users who have not signed up yet; this is a non-fatal warning.
   */
  private List<String> findUnknownUsernames(String whitelist) {
    List<String> unknown = new ArrayList<>();
    for (String token : FilestoreAclChecker.parseList(whitelist)) {
      if (FilestoreAclChecker.EVERYONE.equals(token)) {
        continue;
      }
      try {
        if (userManager.getUserByUsername(token) == null) {
          unknown.add(token);
        }
      } catch (RuntimeException notFound) {
        unknown.add(token);
      }
    }
    return unknown;
  }

  @PostMapping("/delete")
  @ResponseBody
  public Boolean deleteFileSystem(@RequestParam("fileSystemId") Long id) {
    assertIsSysAdmin();
    return nfsManager.deleteNfsFileSystem(id);
  }
}
