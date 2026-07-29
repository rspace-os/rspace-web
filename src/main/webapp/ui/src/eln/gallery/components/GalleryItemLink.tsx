import Link from "@mui/material/Link";
import React from "react";
import NavigateContext from "../../../stores/contexts/Navigate";

/**
 * The URL for a Gallery item, live or pinned to one past version.
 *
 * The live view takes no version segment: it is the ordinary, editable item view, and linking
 * straight to it saves a redirect. Both shapes need a matching rule in `urlrewrite.xml`, which is
 * the reason to build them in one place rather than inline at each call site.
 */
export function galleryItemHref(fileId: string, version?: number): string {
  return typeof version === "number" ? `/gallery/item/${fileId}/${version}` : `/gallery/item/${fileId}`;
}

/**
 * A link to a Gallery item, handled in-app.
 *
 * Keeps a real `href` so it can be copied, and leaves modified and non-primary clicks to the
 * browser so "open in new tab" still works. Only a plain left click is intercepted, which is what
 * avoids a full page load.
 */
export function GalleryItemLink({
  href,
  onNavigate,
  children,
}: {
  href: string;
  onNavigate?: () => void;
  children: React.ReactNode;
}): React.ReactNode {
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();
  return (
    <Link
      href={href}
      onClick={(e: React.MouseEvent) => {
        if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || e.button !== 0) return;
        e.preventDefault();
        navigate(href);
        onNavigate?.();
      }}
    >
      {children}
    </Link>
  );
}
