package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.model.User;

/** Encapsulates Job retrieval and querying */
public interface JobsApiHandler {

  ApiJob getJob(Long jobId, User apiClient);
}
