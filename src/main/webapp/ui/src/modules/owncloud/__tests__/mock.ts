import { HttpResponse, http } from "msw";
import { mockOAuthAuthorize } from "../../../__tests__/e2e/mocks/mockOAuthAuthorize.ts";

export const MOCK_FILE_NAME = "mock-document.pdf";

const WEBDAV_MULTISTATUS = `<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
  <d:response>
    <d:href>/owncloud/remote.php/webdav/</d:href>
    <d:propstat>
      <d:prop>
        <d:getlastmodified>Mon, 01 Jan 2024 00:00:00 GMT</d:getlastmodified>
        <d:getetag>&quot;root-etag&quot;</d:getetag>
        <d:resourcetype><d:collection/></d:resourcetype>
        <oc:fileid>1</oc:fileid>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/owncloud/remote.php/webdav/${MOCK_FILE_NAME}</d:href>
    <d:propstat>
      <d:prop>
        <d:getlastmodified>Mon, 01 Jan 2024 00:00:00 GMT</d:getlastmodified>
        <d:getetag>&quot;file-etag&quot;</d:getetag>
        <d:getcontenttype>application/pdf</d:getcontenttype>
        <d:resourcetype/>
        <oc:fileid>2</oc:fileid>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
</d:multistatus>`;

const WEBDAV_ROOT_PATH = "/owncloud/remote.php/webdav/";
const WEBDAV_FILE_PATH = `/owncloud/remote.php/webdav/${MOCK_FILE_NAME}`;

const corsPreflightResponse = () =>
  new HttpResponse(null, {
    status: 204,
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "PROPFIND, GET, OPTIONS",
      "Access-Control-Allow-Headers": "*",
    },
  });

const webdavResponse = (request: Request) => {
  if (request.method !== "PROPFIND") {
    return new HttpResponse(null, { status: 405, headers: { "Access-Control-Allow-Origin": "*" } });
  }
  return new HttpResponse(WEBDAV_MULTISTATUS, {
    status: 207,
    headers: { "Content-Type": "application/xml", "Access-Control-Allow-Origin": "*" },
  });
};

export const owncloudHandlers = [
  mockOAuthAuthorize("/owncloud/index.php/apps/oauth2/authorize", "mock-owncloud-auth-code"),

  http.post("/owncloud/index.php/apps/oauth2/api/v1/token", () =>
    HttpResponse.json({
      access_token: "mock-owncloud-access-token",
      refresh_token: "mock-owncloud-refresh-token",
      token_type: "bearer",
      expires_in: 3600,
      user_id: "mock-owncloud-user",
    }),
  ),

  http.options(WEBDAV_ROOT_PATH, corsPreflightResponse),
  http.options(WEBDAV_FILE_PATH, corsPreflightResponse),
  http.all(WEBDAV_ROOT_PATH, ({ request }) => webdavResponse(request)),
  http.all(WEBDAV_FILE_PATH, ({ request }) => webdavResponse(request)),
];
