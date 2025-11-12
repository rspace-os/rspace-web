package com.researchspace.webapp.integrations.raid;

import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import com.researchspace.model.dtos.RaidGroupAssociation;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.RaIDServiceManager;
import com.researchspace.service.raid.RaIDServerConfigurationDTO;
import com.researchspace.service.raid.RaIDServiceClientAdapter;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import java.security.Principal;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.HttpClientErrorException;

@Controller
@RequestMapping("/apps/raid")
public class RaIDController extends BaseOAuth2Controller {

  @Setter(value = AccessLevel.PROTECTED) // test purposes
  @Autowired
  private RaIDServiceClientAdapter raidServiceClientAdapter;

  @Setter(value = AccessLevel.PROTECTED) // test purposes
  @Autowired
  private RaIDServiceManager raidServiceManager;

  /***
   * This method takes care of getting the list of the created raid by the user and remove from it
   * the ones that have been already associated in RSpace
   *
   * @param principal
   * @return the list of RaID created by the user without the ones that have been already associated
   */
  @GetMapping()
  @ResponseBody
  @ResponseStatus(HttpStatus.CREATED)
  public AjaxReturnObject<Set<RaIDReferenceDTO>> getRaidListByUser(Principal principal) {
    Set<RaIDReferenceDTO> result = new HashSet<>();
    RaIDReferenceDTO errorBean = new RaIDReferenceDTO();
    BindingResult errors = new BeanPropertyBindingResult(errorBean, "RaIDReferenceDTO");
    try {
      Map<String, RaIDServerConfigurationDTO> serverByAlias =
          raidServiceClientAdapter.getServerMapByAlias();
      Set<RaIDReferenceDTO> externalRaIDList = null;
      // for each server get the list of RaID
      for (String currentServerAlias : serverByAlias.keySet()) {
        try {
          errorBean.setRaidServerAlias(currentServerAlias);
          externalRaIDList =
              raidServiceClientAdapter.getRaIDList(principal.getName(), currentServerAlias);
          if (externalRaIDList != null && !externalRaIDList.isEmpty()) {
            // remove from the external list the RaID that have already been associated
            // by this user
            Set<RaIDReferenceDTO> userRaidAlreadyAssociated =
                raidServiceManager
                    .getAssociatedRaidsByUserAndAlias(
                        userManager.getUserByUsername(principal.getName()), currentServerAlias)
                    .stream()
                    .map(RaidGroupAssociation::getRaid)
                    .collect(Collectors.toSet());
            externalRaIDList.removeAll(userRaidAlreadyAssociated);

            result.addAll(externalRaIDList);
          }
        } catch (HttpClientErrorException e) {
          log.warn("error connecting to RaID for serverAlias {}:", currentServerAlias, e);
          errors.rejectValue(
              "raidServerAlias",
              "connectionError",
              "no connection to RaID for serverAlias " + currentServerAlias);
        }
      }
    } catch (Exception ex) {
      log.error("Not able to get RaID list for the user {}:", principal.getName(), ex);
      errors.reject(
          "raidList", null, "Not able to get RaID list for the user " + principal.getName());
    }
    if (errors.hasErrors()) {
      ErrorList el = inputValidator.populateErrorList(errors, new ErrorList());
      return new AjaxReturnObject<>(result, el);
    }
    return new AjaxReturnObject<>(result, null);
  }

  /***
   * Returns the raid DTO if there is an association, otherwise null
   *
   * @param projectGroupId
   * @param raidServerAlias
   * @return the raid DTO if there is an association, otherwise null
   */
  @GetMapping("/{raidServerAlias}/projects/{projectGroupId}")
  @ResponseBody
  @ResponseStatus(HttpStatus.OK)
  public AjaxReturnObject<RaidGroupAssociation> getAssociatedRaidToProject(
      @PathVariable String raidServerAlias,
      @PathVariable Long projectGroupId,
      Principal principal) {
    Optional<RaidGroupAssociation> result = Optional.empty();
    BindingResult errors = new BeanPropertyBindingResult(null, "raidGroupAssociation");
    //    User user = userManager.getAuthenticatedUserInSession();
    try {
      Set<RaidGroupAssociation> userRaidAlreadyAssociated =
          raidServiceManager.getAssociatedRaidsByUserAndAlias(
              userManager.getUserByUsername(principal.getName()), raidServerAlias);
      if (!userRaidAlreadyAssociated.isEmpty()) {
        result =
            userRaidAlreadyAssociated.stream()
                .filter(raid -> raid.getProjectGroupId().equals(projectGroupId))
                .findAny();
      }
    } catch (Exception e) {
      log.error("Not able to get RaID list for the user {}:", principal.getName(), e);
      errors.reject(
          "raidList", null, "Not able to get RaID list for the user " + principal.getName());
    }
    if (errors.hasErrors()) {
      ErrorList el = inputValidator.populateErrorList(errors, new ErrorList());
      return new AjaxReturnObject<>(null, el);
    }
    return new AjaxReturnObject<>(result.orElse(new RaidGroupAssociation()), null);
  }

  // TODO[nik]:  remove the GET since it is here just for testing without UI
  @GetMapping("/associate/{projectGroupId}/{raidServerAlias}")
  @ResponseStatus(HttpStatus.CREATED)
  @ResponseBody
  public String associateRaidToGroup_GET(
      @PathVariable Long projectGroupId,
      @PathVariable String raidServerAlias,
      @RequestParam(name = "raidIdentifier") String raidIdentifier)
      throws BindException {
    RaidGroupAssociation input =
        new RaidGroupAssociation(
            projectGroupId, new RaIDReferenceDTO(raidServerAlias, raidIdentifier));
    return associateRaidToGroup(input);
  }

  @PostMapping("/associate")
  @ResponseStatus(HttpStatus.CREATED)
  @ResponseBody
  public String associateRaidToGroup(@RequestBody RaidGroupAssociation raidGroupAssociation)
      throws BindException {
    validateInput(raidGroupAssociation);
    BindingResult errors =
        new BeanPropertyBindingResult(raidGroupAssociation, "raidGroupAssociation");
    Group group = null;
    try {
      User user = userManager.getAuthenticatedUserInSession();
      group = groupManager.getGroup(raidGroupAssociation.getProjectGroupId());
      Validate.isTrue(
          group.getGroupType().equals(GroupType.PROJECT_GROUP),
          "Only Project Group can be associated to RaID");

      if (group.getRaid() == null) {
        raidServiceManager.bindRaidToGroupAndSave(user, raidGroupAssociation);
        log.info(
            "ProjectGroupId \""
                + group.getId()
                + "\" has been associated to RaID \""
                + raidGroupAssociation.getRaid().getRaidIdentifier()
                + "\"");
      } else {
        String message =
            "The group with projectGroupId"
                + raidGroupAssociation.getProjectGroupId()
                + " was had already a RaID association. Please remove it first before to associate"
                + " another RaID";
        errors.rejectValue("projectGroupId", "raid.alreadyAssociated", message);
        log.error(message);
      }
    } catch (Exception e) {
      errors.reject(
          "associateRaidToGroup", "Not able to associate RaID to group: " + e.getMessage());
      log.error("Not able to associate RaID to group: " + e.getMessage());
    }
    throwBindExceptionIfErrors(errors);

    return "redirect:/groups/view/" + raidGroupAssociation.getProjectGroupId();
  }

  // TODO[nik]:  remove the GET since it is here just for testing without UI
  @GetMapping("/disassociate/{projectGroupId}")
  public @ResponseBody String disassociateRaidToGroup_GET(@PathVariable Long projectGroupId)
      throws BindException {
    return disassociateRaidFromGroup(projectGroupId);
  }

  @PostMapping("/disassociate/{projectGroupId}")
  public @ResponseBody String disassociateRaidFromGroup(@PathVariable Long projectGroupId)
      throws BindException {
    Group group = null;
    BindingResult errors = new BeanPropertyBindingResult(projectGroupId, "projectGroupId");
    try {
      User user = userManager.getAuthenticatedUserInSession();
      group = groupManager.getGroup(projectGroupId);
      Validate.isTrue(
          group.getGroupType().equals(GroupType.PROJECT_GROUP),
          "Only Project Group can be disassociated to RaID");

      if (group.getRaid() != null) {
        raidServiceManager.unbindRaidFromGroupAndSave(user, projectGroupId);
        log.info("ProjectGroupId \"" + projectGroupId + "\" has not more RaID associated");
      } else {
        errors.rejectValue(
            "projectGroupId",
            "raid.noAssociationFound",
            "The group with projectGroupId "
                + projectGroupId
                + " has no RaiDs already associated.");
        log.error(
            "The group with projectGroupId "
                + projectGroupId
                + " has no RaiDs already associated.");
      }
    } catch (Exception e) {
      errors.reject(
          "disassociateRaidFromGroup",
          "Not able to disassociate RaID from group: " + e.getMessage());
      log.error("Not able to disassociate RaID from group: " + e.getMessage());
    }
    throwBindExceptionIfErrors(errors);

    return "redirect:/groups/view/" + projectGroupId;
  }

  private static void validateInput(RaidGroupAssociation raidGroupAssociation) {
    Validate.isTrue(raidGroupAssociation.getProjectGroupId() != null, "projectGroupId is missing");
    Validate.isTrue(
        StringUtils.isNotBlank(raidGroupAssociation.getRaid().getRaidIdentifier()),
        "raidIdentifier is missing");
    Validate.isTrue(
        StringUtils.isNotBlank(raidGroupAssociation.getRaid().getRaidServerAlias()),
        "raidServerAlias is missing");
  }

  private void throwBindExceptionIfErrors(BindingResult errors) throws BindException {
    if (errors != null && errors.hasErrors()) {
      throw new BindException(errors);
    }
  }
}
