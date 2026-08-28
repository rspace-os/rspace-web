import { HttpResponse, http } from "msw";

export const MOCK_EGNYTE_ACCESS_TOKEN = "mock-egnyte-access-token";
export const MOCK_EGNYTE_FILE_NAME = "mock-egnyte-file.txt";
export const MOCK_EGNYTE_FOLDER_NAME = "Mock Folder";

function appBaseUrl(): string {
  return process.env.RSPACE_BASE_URL ?? "http://localhost:8080";
}

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
};

export const egnyteHandlers = [
  http.get("/", () => new HttpResponse("ok", { status: 200 })),

  http.options("/pubapi/v1/fs/", () => new HttpResponse(null, { status: 204, headers: CORS_HEADERS })),

  http.get("/puboauth/token", ({ request }) => {
    const incomingState = new URL(request.url).searchParams.get("state");
    const target = new URL("/scripts/externalTinymcePlugins/egnyte/dialog.html", appBaseUrl());
    const fragment = new URLSearchParams({ access_token: MOCK_EGNYTE_ACCESS_TOKEN });
    if (incomingState) fragment.set("state", incomingState);
    target.hash = fragment.toString();
    return HttpResponse.redirect(target.toString(), 302);
  }),

  http.get("/pubapi/v1/fs/", () =>
    HttpResponse.json(
      {
        count: 1,
        offset: 0,
        path: "/",
        name: "Shared",
        is_folder: true,
        folder_id: "mock-folder-id",
        folders: [],
        files: [
          {
            name: MOCK_EGNYTE_FILE_NAME,
            group_id: "mock-file-group-id",
            is_folder: false,
            size: 42,
          },
        ],
      },
      { headers: CORS_HEADERS },
    ),
  ),
];
