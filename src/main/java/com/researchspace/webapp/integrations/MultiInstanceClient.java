package com.researchspace.webapp.integrations;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public interface MultiInstanceClient<T> {

  Map<String, T> getServerMapByAlias();

  T getServerConfiguration(@NotNull String serverAlias);

  String getApiBaseUrl(@NotNull String serverAlias);

  String getAuthBaseUrl(@NotNull String serverAlias);
}
