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

const NON_BROWSER_SELECTIONS = ["api", "setup"];

/** `E2E_BROWSER` accepts a comma-separated list, e.g. `mobile,api`; empty selects every project. */
export function parseProjectSelection(selection: string): string[] {
  return selection
    .split(",")
    .map((name) => name.trim())
    .filter(Boolean);
}

/** Shared by the runner and authentication setup so each selected user has login state. */
export function selectBrowserProjects(selection: string, { cloud }: { cloud: boolean }): BrowserProject[] {
  const available = cloud ? browserProjects.filter(({ name }) => name !== "mobile") : browserProjects;
  const names = parseProjectSelection(selection);
  if (names.length === 0) return available;
  return names
    .filter((name) => !NON_BROWSER_SELECTIONS.includes(name))
    .map((name) => {
      const project = available.find((candidate) => candidate.name === name);
      if (!project) {
        throw new Error(
          `Unknown E2E_BROWSER "${name}"${cloud ? " with E2E_CLOUD=true" : ""}. Choose ${available.map(({ name }) => name).join(", ")}, api, or setup.`,
        );
      }
      return project;
    });
}
