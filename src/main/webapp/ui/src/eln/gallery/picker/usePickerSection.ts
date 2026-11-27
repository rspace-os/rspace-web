import { useEffect, useState } from "react";
import useUiPreference, { PREFERENCES } from "../../../hooks/api/useUiPreference";
import type { GallerySection } from "../common";

/**
 * The picker's selected section. Without an initial section the picker opens where the user last
 * left it and remembers each change; with one, it opens there each time and leaves that preference
 * alone, so a caller that always opens on one section does not move every other picker.
 */
export default function usePickerSection(
  initialSection?: GallerySection,
  open = true,
): [GallerySection, (section: GallerySection) => void] {
  const remembered = useUiPreference<GallerySection>(PREFERENCES.GALLERY_PICKER_INITIAL_SECTION, {
    defaultValue: "Chemistry",
  });
  const local = useState<GallerySection>(initialSection ?? "Chemistry");
  const setLocal = local[1];
  // a picker can stay mounted while closed, so go back to the caller's section on every opening
  useEffect(() => {
    if (open && initialSection) setLocal(initialSection);
  }, [open, initialSection, setLocal]);
  return initialSection ? local : remembered;
}
