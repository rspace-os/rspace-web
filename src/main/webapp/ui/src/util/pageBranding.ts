import { ACCENT_COLOR as GALLERY_COLOR } from "../assets/branding/rspace/gallery";
import { ACCENT_COLOR as OTHER_COLOR } from "../assets/branding/rspace/other";
import { ACCENT_COLOR as WORKSPACE_COLOR } from "../assets/branding/rspace/workspace";

type PageBrandingKey = "workspace" | "other" | "system" | "myRSpace" | "gallery" | "unknown";

export function currentPageKey(): PageBrandingKey {
  const pages: Record<string, PageBrandingKey> = {
    workspace: "workspace",
    notebookEditor: "workspace",
    dashboard: "other",
    system: "system",
    community: "system",
    record: "myRSpace",
    userform: "myRSpace",
    directory: "myRSpace",
    audit: "myRSpace",
    import: "myRSpace",
    groups: "myRSpace",
    gallery: "gallery",
  };
  const firstPathFragment = window.location.pathname.split("/")[1];
  if (firstPathFragment in pages) return pages[firstPathFragment];
  return "unknown";
}

export function color(
  page: PageBrandingKey | string,
): typeof WORKSPACE_COLOR | typeof GALLERY_COLOR | typeof OTHER_COLOR {
  if (page === "workspace" || page === "Workspace") return WORKSPACE_COLOR;
  if (page === "gallery" || page === "Gallery") return GALLERY_COLOR;
  return OTHER_COLOR;
}
