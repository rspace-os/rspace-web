package com.researchspace.search.impl;

import com.axiope.search.FieldNames;
import com.axiope.search.SearchConstants;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.FormManager;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.lucene.index.Term;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Component;

/** Converts search options and terms from UI into Lucene Search terms. */
@Component
public class LuceneSearchTermListFactory {

  @Autowired private IPermissionUtils permUtils;
  @Autowired private FormManager formManager;

  /**
   * Create a list of Term (Lucene search) checking the field option (FullText, Tag, Name, Form,
   * Date, etc). These terms will directly get passed to Lucene for finding record matches, so it
   * should only contain terms with actual fields and content to match.
   *
   * @param user performing the search
   * @param options search query field data
   * @param terms search query term data
   * @return Map linking term fields to a list of all the terms of that type, guaranteeing that if a
   *     field is present in the map, it has at least 1 non-empty term. (I.e. empty terms are
   *     skipped)
   */
  public Map<String, List<Term>> getTermList(User user, String[] options, String[] terms) {
    Map<String, List<Term>> map = new HashMap<>();

    for (int i = 0; i < terms.length; i++) {
      final String term = terms[i];
      if (term.equals("")) {
        continue; // Skip empty terms
      }

      switch (options[i]) {
        case SearchConstants.ALL_SEARCH_OPTION:
          Arrays.asList(
                  FieldNames.FIELD_DATA, FieldNames.DOC_TAG, FieldNames.NAME, FieldNames.FORM_NAME)
              .forEach(
                  fieldName -> {
                    map.putIfAbsent(fieldName, new ArrayList<>());
                    map.get(fieldName).add(new Term(fieldName, term));
                  });
          if (isFormGlobalId(term)) {
            map.putIfAbsent(FieldNames.FORM_STABLE_ID, new ArrayList<>());
            map.get(FieldNames.FORM_STABLE_ID).add(getFormStableIdTerm(user, term));
          }
          break;

        case SearchConstants.FULL_TEXT_SEARCH_OPTION:
          map.putIfAbsent(FieldNames.FIELD_DATA, new ArrayList<>());
          map.get(FieldNames.FIELD_DATA).add(new Term(FieldNames.FIELD_DATA, term));
          break;

        case SearchConstants.TAG_SEARCH_OPTION:
          map.putIfAbsent(FieldNames.DOC_TAG, new ArrayList<>());
          map.get(FieldNames.DOC_TAG).add(new Term(FieldNames.DOC_TAG, term));
          break;

        case SearchConstants.NAME_SEARCH_OPTION:
          map.putIfAbsent(FieldNames.NAME, new ArrayList<>());
          map.get(FieldNames.NAME).add(new Term(FieldNames.NAME, term));
          break;

        case SearchConstants.FORM_SEARCH_OPTION:
          if (isFormGlobalId(term)) {
            map.putIfAbsent(FieldNames.FORM_STABLE_ID, new ArrayList<>());
            map.get(FieldNames.FORM_STABLE_ID).add(getFormStableIdTerm(user, term));
          } else {
            map.putIfAbsent(FieldNames.FORM_NAME, new ArrayList<>());
            map.get(FieldNames.FORM_NAME).add(new Term(FieldNames.FORM_NAME, term));
          }
          break;

        case SearchConstants.MODIFICATION_DATE_SEARCH_OPTION:
          map.putIfAbsent(FieldNames.MODIFICATION_DATE, new ArrayList<>());
          map.get(FieldNames.MODIFICATION_DATE).add(new Term(FieldNames.MODIFICATION_DATE, term));
          break;

        case SearchConstants.CREATION_DATE_SEARCH_OPTION:
          map.putIfAbsent(FieldNames.CREATION_DATE, new ArrayList<>());
          map.get(FieldNames.CREATION_DATE).add(new Term(FieldNames.CREATION_DATE, term));
          break;

        case SearchConstants.OWNER_SEARCH_OPTION:
          map.putIfAbsent(FieldNames.OWNER, new ArrayList<>());
          map.get(FieldNames.OWNER).add(new Term(FieldNames.OWNER, term));
          break;

        case SearchConstants.FROM_TEMPLATE_SEARCH_OPTION:
          map.putIfAbsent(FieldNames.TEMPLATE, new ArrayList<>());
          map.get(FieldNames.TEMPLATE).add(new Term(FieldNames.TEMPLATE, term));
          break;

        case SearchConstants.RECORDS_SEARCH_OPTION:
          // Gets parsed in LuceneSearchCfg for now, should not create a term
          break;

        case SearchConstants.INVENTORY_SEARCH_OPTION:
          Arrays.asList(
                  FieldNames.NAME,
                  FieldNames.INV_TAGS,
                  FieldNames.DESCRIPTION,
                  FieldNames.FIELD_DATA,
                  /* FieldNames.BARCODE - barcode matches handled by direct db search rather than lucene */
                  FieldNames.BARCODE_FIELD_DATA)
              .forEach(
                  fieldName -> {
                    map.putIfAbsent(fieldName, new ArrayList<>());
                    map.get(fieldName).add(new Term(fieldName, term));
                  });
          break;
      }
    }

    return map;
  }

  /**
   * For a term that contains a valid global form id that the user can access, it will return a term
   * with stable id of the form.
   *
   * @param user
   * @param term
   * @return term with the stable id
   */
  private Term getFormStableIdTerm(User user, String term) {
    Long formId = new GlobalIdentifier(term).getDbId();
    try {
      RSForm form = formManager.get(formId, user);
      if (!permUtils.isPermitted(form, PermissionType.READ, user)) {
        // User is searching for a form that he cannot read by id.
        throw new AuthorizationException(
            String.format(
                "Either form %s does not exist or %s does not have read permission",
                term, user.getUsername()));
      }
      return new Term(FieldNames.FORM_STABLE_ID, form.getStableID());
    } catch (ObjectRetrievalFailureException e) {
      // Form does not exist
      throw new AuthorizationException(
          String.format(
              "Either form %s does not exist or %s does not have read permission",
              term, user.getUsername()));
    }
  }

  private static boolean isFormGlobalId(String term) {
    return Pattern.matches("FM\\d+", term);
  }
}
