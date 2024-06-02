package com.researchspace.dao;

import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.FormType;
import java.io.Serializable;
import java.util.Optional;

public interface AbstractFormDao<T extends AbstractForm, PK extends Serializable>
    extends GenericDao<T, PK> {

  Optional<FormType> getTypeById(Long id);
}
