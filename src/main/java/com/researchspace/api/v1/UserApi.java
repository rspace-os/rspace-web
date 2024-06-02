package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.api.v1.model.ApiUserPost;
import com.researchspace.model.User;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/v1/users")
public interface UserApi {

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiUser createNewUser(
      @RequestBody @Valid ApiUserPost userToCreate, BindingResult errors, User sysadmin)
      throws BindException;
}
