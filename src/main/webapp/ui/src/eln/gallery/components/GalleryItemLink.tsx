import Link from "@mui/material/Link";
import React from "react";
import NavigateContext from "../../../stores/contexts/Navigate";

/**
 * The URL for a Gallery item, live or pinned to one past version. Built in one
 * place because both shapes need a matching rule in `urlrewrite.xml`.
 */
export function galleryItemHref(fileId: string, version?: number): string {
  return typeof version === "number" ? `/gallery/item/${fileId}/${version}` : `/gallery/item/${fileId}`;
}

/**
 * A link to a Gallery item, handled in-app. Keeps a real `href` so it can be
 * copied, and intercepts only a plain left click so "open in new tab" still
 * works.
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
