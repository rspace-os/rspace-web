package com.researchspace.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary shared by a scoped resource lookup and its mutation. */
@Service
@Transactional
public class ResourceMutationManager {
  /** Runs the scope check and mutation in the same transaction. */
  public <R> R execute(Supplier<R> mutation) {
    return mutation.get();
  }
}
