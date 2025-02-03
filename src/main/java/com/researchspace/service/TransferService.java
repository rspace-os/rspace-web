package com.researchspace.service;

import com.researchspace.model.User;

public interface TransferService {
  void transferOwnership(User originalOwner, User newOwner);
}
