import { HttpResponse, http } from "msw";

export const MOCK_REPO_FULL_NAME = "mock-org/mock-repo";
export const MOCK_REPO_DESCRIPTION = "A mock repository for e2e testing.";
export const MOCK_BRANCH = "main";
export const MOCK_FILE_PATH = "README.md";
export const MOCK_FILE_SHA = "mock-github-blob-sha";

function appBaseUrl(): string {
  return process.env.RSPACE_BASE_URL ?? "http://localhost:8080";
}

export const githubHandlers = [
  http.get("/github/oauth/authorize", () => {
    const target = new URL("/github/redirect_uri", appBaseUrl());
    target.searchParams.set("code", "mock-github-auth-code");
    return HttpResponse.redirect(target.toString(), 302);
  }),

  http.post("/github/oauth/token", () =>
    HttpResponse.json({
      access_token: "mock-github-access-token",
      scope: "repo,user",
      token_type: "bearer",
    }),
  ),

  http.get("/github-api/user/repos", () =>
    HttpResponse.json([{ full_name: MOCK_REPO_FULL_NAME, description: MOCK_REPO_DESCRIPTION }]),
  ),

  http.get("/github-api/repos/:owner/:repo", ({ params }) =>
    HttpResponse.json({
      full_name: `${params.owner}/${params.repo}`,
      default_branch: MOCK_BRANCH,
    }),
  ),

  http.get("/github-api/repos/:owner/:repo/git/trees/:sha", ({ params }) =>
    HttpResponse.json({
      sha: String(params.sha),
      url: `https://api.github.com/repos/${params.owner}/${params.repo}/git/trees/${params.sha}`,
      tree: [{ path: MOCK_FILE_PATH, type: "blob", sha: MOCK_FILE_SHA }],
    }),
  ),
];
