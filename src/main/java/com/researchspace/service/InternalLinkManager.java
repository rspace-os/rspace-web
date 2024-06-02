package com.researchspace.service;

import com.researchspace.model.InternalLink;
import com.researchspace.model.User;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;

/** Service for works with internal links information */
public interface InternalLinkManager {

  /** Retrieves internal links from non-deleted documents to target record */
  List<InternalLink> getLinksPointingToRecord(long targetRecordId);

  /**
   * Makes a new {@link InternalLink} association between a field's containing document and target
   * record
   *
   * @param srcFieldId the id of the text field into which the link is being made
   * @param linkedRecordId Id of a Folder, notebook or document
   * @param subject
   * @return <code>true</code> if link was created, <code>false</code> otherwise (e.g. if link
   *     already exists).
   * @throws AuthorizationException if <code>subject</code> doesn't have WRITE permission on
   *     srcRecord and READ permission on <code>linkedRecord</code>
   * @throws IllegalArgumentException if linkedRecordId is not a folder, notebook or document, or if
   *     the field is not a text field
   */
  boolean createInternalLink(Long srcFieldId, Long linkedRecordId, User subject);
}
