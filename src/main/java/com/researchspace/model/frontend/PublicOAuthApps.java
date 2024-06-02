package com.researchspace.model.frontend;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** Simple POJO for returning information on the User Profile page about a user's OAuth apps */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublicOAuthApps {
  @NonNull private List<PublicOAuthAppInfo> oAuthApps;
}
