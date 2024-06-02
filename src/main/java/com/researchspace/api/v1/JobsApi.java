package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.model.User;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Methods to interact with asynchronous jobs */
@RequestMapping("/api/v1/jobs")
public interface JobsApi {

  /**
   * Retrieves a Job based on ID
   *
   * @param id
   * @param response
   * @param user
   * @return An {@link ApiJob}
   * @throws AuthorizationException if api client is not the owner of the job
   * @throws ResourceNotFoundException if job id does not exist
   */
  @GetMapping("/{id}")
  ApiJob get(Long id, HttpServletResponse response, User user);
}
