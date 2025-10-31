package com.researchspace.webapp.integrations;

import java.util.Map;
import javax.validation.constraints.NotNull;

public interface MultiInstanceClient<T> {

  Map<String, T> getServerMapByAlias();

  T getServerConfiguration(@NotNull String serverAlias);

  String getApiBaseUrl(@NotNull String serverAlias);

  String getAuthBaseUrl(@NotNull String serverAlias);
}
