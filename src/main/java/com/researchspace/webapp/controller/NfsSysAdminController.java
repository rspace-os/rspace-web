package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.service.NfsManager;
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
  public Long saveFileSystem(@RequestBody NfsFileSystem nfsFileSystem) {
    assertIsSysAdmin();
    nfsManager.saveNfsFileSystem(nfsFileSystem);
    return nfsFileSystem.getId();
  }

  @PostMapping("/delete")
  @ResponseBody
  public Boolean deleteFileSystem(@RequestParam("fileSystemId") Long id) {
    assertIsSysAdmin();
    return nfsManager.deleteNfsFileSystem(id);
  }
}
