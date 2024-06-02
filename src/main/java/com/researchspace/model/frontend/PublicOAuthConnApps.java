package com.researchspace.model.frontend;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

/** Simple POJO for returning information on the User Profile page about a user's connected apps */
@Data
@AllArgsConstructor
public class PublicOAuthConnApps {
  @NonNull private List<PublicOAuthConnAppInfo> oAuthConnectedApps;
}
