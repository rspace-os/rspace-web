import { useState } from "react";
import useUiPreference, { PREFERENCES } from "../../../hooks/api/useUiPreference";
import type { GallerySection } from "../common";

/**
 * The picker's selected section. Without an initial section the picker opens where the user last
 * left it and remembers each change; with one, it opens there and leaves that preference alone, so
 * a caller that always opens on one section does not move every other picker.
 */
export default function usePickerSection(
  initialSection?: GallerySection,
): [GallerySection, (section: GallerySection) => void] {
  const remembered = useUiPreference<GallerySection>(PREFERENCES.GALLERY_PICKER_INITIAL_SECTION, {
    defaultValue: "Chemistry",
  });
  const local = useState<GallerySection>(initialSection ?? "Chemistry");
  return initialSection ? local : remembered;
}
