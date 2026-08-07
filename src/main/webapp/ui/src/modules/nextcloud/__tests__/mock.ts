import { HttpResponse, http } from "msw";

export const MOCK_FILE_NAME = "mock-nextcloud-document.pdf";

const WEBDAV_MULTISTATUS = `<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
  <d:response>
    <d:href>/nextcloud/remote.php/webdav/</d:href>
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
    <d:href>/nextcloud/remote.php/webdav/${MOCK_FILE_NAME}</d:href>
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

export const nextcloudHandlers = [
  http.get("/nextcloud/index.php/apps/oauth2/authorize", ({ request }) => {
    const redirectUri = new URL(request.url).searchParams.get("redirect_uri");
    if (!redirectUri) {
      return new HttpResponse("Missing redirect_uri", { status: 400 });
    }
    const target = new URL(redirectUri);
    target.searchParams.set("code", "mock-nextcloud-auth-code");
    return HttpResponse.redirect(target.toString(), 302);
  }),

  http.post("/nextcloud/index.php/apps/oauth2/api/v1/token", () =>
    HttpResponse.json({
      access_token: "mock-nextcloud-access-token",
      refresh_token: "mock-nextcloud-refresh-token",
      token_type: "bearer",
      expires_in: 3600,
      user_id: "mock-nextcloud-user",
    }),
  ),

  http.options(
    "/nextcloud/remote.php/webdav*",
    () =>
      new HttpResponse(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "PROPFIND, GET, OPTIONS",
          "Access-Control-Allow-Headers": "*",
        },
      }),
  ),
  http.all(
    "/nextcloud/remote.php/webdav*",
    () =>
      new HttpResponse(WEBDAV_MULTISTATUS, {
        status: 207,
        headers: { "Content-Type": "application/xml", "Access-Control-Allow-Origin": "*" },
      }),
  ),
];
