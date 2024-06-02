package com.researchspace.service;

import com.researchspace.model.ImageBlob;
import com.researchspace.model.record.IconEntity;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface IconImageManager extends GenericManager<ImageBlob, Long> {
  IconEntity getIconEntity(Long id);

  IconEntity saveIconEntity(IconEntity iconEntity, boolean updateRSFormTable);

  List<Long> getAllIconIds();

  /**
   * Facade method to loads the icon entity with the given id, and writes its content to an
   * outputstream
   *
   * @param id The id of an Icon
   * @param out an output stream to write to.
   * @param defaultIconSupplier A supplier for a default icon image if the desired icon could not be
   *     retrieved.
   * @return The loaded Icon or null optional
   * @throws IOException
   */
  Optional<IconEntity> getIconEntity(
      Long id, OutputStream out, Supplier<byte[]> defaultIconSupplier) throws IOException;
}
