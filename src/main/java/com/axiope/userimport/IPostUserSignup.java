package com.axiope.userimport;

import com.researchspace.model.User;

public interface IPostUserSignup extends IPostUserCreationSetUp {

  String getRedirect(User savedUser);
}
