package com.researchspace.webapp.controller;

import com.researchspace.model.Organisation;
import com.researchspace.model.ProductType;
import com.researchspace.model.field.ErrorList;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller("organisationController")
@RequestMapping("/organisation*")
@Product(ProductType.COMMUNITY)
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.DEFAULT)
public class OrganisationController extends BaseController {

  @ResponseBody
  @GetMapping("/ajax/approved")
  public AjaxReturnObject<List<Organisation>> getApprovedOrganisations(
      @RequestParam(value = "term", required = true) String term) {
    final int minLength = 4;
    if (term.length() < minLength) {
      ErrorList error =
          ErrorList.of(getText("errors.minlength", new String[] {"Search term", "4"}));
      return new AjaxReturnObject<List<Organisation>>(new ArrayList<Organisation>(), error);
    }

    List<Organisation> list = organisationManager.getApprovedOrganisations(term);
    return new AjaxReturnObject<List<Organisation>>(list, null);
  }
}
