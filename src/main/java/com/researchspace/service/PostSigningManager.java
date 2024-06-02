package com.researchspace.service;

import com.researchspace.model.views.SigningResult;
import org.springframework.scheduling.annotation.Async;

public interface PostSigningManager {

  /**
   * Performs post-signing operations
   *
   * @param signatureResult
   */
  @Async(value = "signTaskExecutor")
  void postRecordSign(SigningResult signatureResult);
}
