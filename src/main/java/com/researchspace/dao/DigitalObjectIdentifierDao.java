package com.researchspace.dao;

import com.researchspace.model.inventory.DigitalObjectIdentifier;
import java.util.Optional;

/** For DAO operations on inventory item identifier */
public interface DigitalObjectIdentifierDao extends GenericDao<DigitalObjectIdentifier, Long> {

  Optional<DigitalObjectIdentifier> getLastPublishedIdentifierByPublicLink(String publicLink);
}
