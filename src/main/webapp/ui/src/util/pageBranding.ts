import { ACCENT_COLOR as GALLERY_COLOR } from "../assets/branding/rspace/gallery";
import { ACCENT_COLOR as OTHER_COLOR } from "../assets/branding/rspace/other";
import { ACCENT_COLOR as WORKSPACE_COLOR } from "../assets/branding/rspace/workspace";

export function currentPage(): string {
    const pages: Record<string, string> = {
        workspace: "Workspace",
        notebookEditor: "Workspace",
        dashboard: "Other",
        system: "System",
        community: "System",
        record: "My RSpace",
        userform: "My RSpace",
        directory: "My RSpace",
        audit: "My RSpace",
        import: "My RSpace",
        groups: "My RSpace",
        gallery: "Gallery",
        oldGallery: "Gallery",
    };
    const firstPathFragment = window.location.pathname.split("/")[1];
    if (firstPathFragment in pages) return pages[firstPathFragment];
    return "Unknown";
}

export function color(page: string): typeof WORKSPACE_COLOR | typeof GALLERY_COLOR | typeof OTHER_COLOR {
    if (page === "Workspace") return WORKSPACE_COLOR;
    if (page === "Gallery") return GALLERY_COLOR;
    return OTHER_COLOR;
}
