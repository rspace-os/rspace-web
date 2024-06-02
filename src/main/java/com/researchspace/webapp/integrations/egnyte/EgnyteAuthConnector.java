package com.researchspace.webapp.integrations.egnyte;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.researchspace.service.PostAnyLoginAction;
import com.researchspace.service.PostFirstLoginAction;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import org.apache.http.client.ClientProtocolException;

public interface EgnyteAuthConnector extends PostFirstLoginAction, PostAnyLoginAction {

  /**
   * Posts an access token request to Egnyte instance, using user-provided credentials.
   *
   * @return json response from Egnyte
   */
  Map<String, Object> queryForEgnyteAccessToken(String egnyteUsername, String egnytePassword)
      throws UnsupportedEncodingException,
          IOException,
          ClientProtocolException,
          JsonParseException,
          JsonMappingException;

  /**
   * Queries Egnyte for user details connected to provided access token.
   *
   * @param token
   * @return map with userinfo properties provided by Egnyte
   * @throws IllegalArgumentException with Egnyte response message if token doesn't work
   */
  Map<String, Object> queryForEgnyteUserInfoWithAccessToken(String token) throws IOException;

  boolean isEgnyteConnectionSetupAndWorking(String username);
}
