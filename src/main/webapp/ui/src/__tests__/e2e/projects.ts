import { type AppUser, USERS } from "./users";

type BrowserProject = {
  name: string;
  browserName: "chromium" | "firefox" | "webkit";
  appUser: AppUser;
};

const browserProjects: BrowserProject[] = [
  { name: "chromium", browserName: "chromium", appUser: USERS.user1a },
  { name: "firefox", browserName: "firefox", appUser: USERS.user3c },
  { name: "webkit", browserName: "webkit", appUser: USERS.user4d },
  { name: "mobile", browserName: "chromium", appUser: USERS.user7g },
];

/** Shared by the runner and authentication setup so each selected user has login state. */
export function selectBrowserProjects(selection: string, { cloud }: { cloud: boolean }): BrowserProject[] {
  if (selection === "api" || selection === "setup") return [];
  const available = cloud ? browserProjects.filter(({ name }) => name !== "mobile") : browserProjects;
  if (!selection) return available;
  const project = available.find(({ name }) => name === selection);
  if (!project) {
    throw new Error(
      `Unknown E2E_BROWSER "${selection}"${cloud ? " with E2E_CLOUD=true" : ""}. Choose ${available.map(({ name }) => name).join(", ")}, api, or setup.`,
    );
  }
  return [project];
}
