package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import java.util.List;
import java.util.Optional;

/** For DAO operations on inventory item identifier */
public interface DigitalObjectIdentifierDao extends GenericDao<DigitalObjectIdentifier, Long> {

  Optional<DigitalObjectIdentifier> getLastPublishedIdentifierByPublicLink(String publicLink);

  List<DigitalObjectIdentifier> getActiveIdentifiersByOwner(User owner);
}
