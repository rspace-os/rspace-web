import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { paperClasses } from "@mui/material/Paper";
import React, { useCallback, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { galleryPictureHtml } from "./bondStrengths/galleryPicture";
import { type BondStrengthsDocumentContent, insertBondStrengthsAfter } from "./bondStrengths/insertIntoDocument";
import ChemCard from "./ChemCard";

interface SidebarItem extends Record<string, string | undefined> {
  id: string;
  imageSrc?: string;
}

interface SidebarInfoProps {
  iframe: HTMLIFrameElement;
}

type TinyMCEEditor = {
  id: string;
  execCommand: (command: string, ui: boolean, value?: string) => void;
  undoManager: { transact: (change: () => void) => unknown };
  nodeChanged: () => void;
};

function isElementNode(node: EventTarget | Node | null): node is Element {
  return typeof node === "object" && node !== null && "nodeType" in node && node.nodeType === Node.ELEMENT_NODE;
}

function isChemImage(element: Element | null): element is HTMLImageElement {
  return element?.tagName.toLowerCase() === "img" && element.classList.contains("chem");
}

function getIframeDocument(iframe: HTMLIFrameElement): Document | null {
  return iframe.contentDocument ?? iframe.contentWindow?.document ?? null;
}

function getRemovedChemElements(node: Node): HTMLImageElement[] {
  const removedChemElements: HTMLImageElement[] = [];

  if (isElementNode(node) && isChemImage(node)) {
    removedChemElements.push(node);
  }

  if (isElementNode(node)) {
    removedChemElements.push(...Array.from(node.querySelectorAll<HTMLImageElement>("img.chem")));
  }

  return removedChemElements;
}

function getCustomEventDetail(event: Event): unknown {
  return "detail" in event ? event.detail : undefined;
}

function getActiveEditor(): TinyMCEEditor | undefined {
  return (globalThis as { tinymce?: { activeEditor?: TinyMCEEditor } }).tinymce?.activeEditor;
}

export default function SidebarInfo({ iframe }: SidebarInfoProps) {
  const [items, setItems] = React.useState<SidebarItem[]>([]);
  const [open, setOpen] = React.useState(false);
  const { t } = useTranslation("workspace");

  const addItem = useCallback((element: HTMLImageElement): void => {
    if (!element.id) {
      return;
    }

    const item: SidebarItem = {
      id: element.id,
      imageSrc: element.getAttribute("src") ?? undefined,
    };

    setItems((oldItems) => [item, ...oldItems.filter((existingItem) => existingItem.id !== item.id)]);
  }, []);

  const removeItem = useCallback((id?: string): void => {
    setItems((oldItems) => oldItems.filter((item) => item.id !== id));
  }, []);

  const closeAll = useCallback((event?: React.MouseEvent<HTMLButtonElement>): void => {
    event?.preventDefault();
    setItems([]);
  }, []);

  const insertBondStrengths = useCallback(
    async (id: string | undefined, content: BondStrengthsDocumentContent): Promise<void> => {
      const chemImage = id ? (getIframeDocument(iframe)?.getElementById(id) ?? null) : null;
      const activeEditor = getActiveEditor();
      if (!isChemImage(chemImage) || !activeEditor) {
        return;
      }

      const fieldId = activeEditor.id.replace(/^\D+/g, "");
      let pictureHtml: string | null = null;
      if (content.imageSrc && fieldId) {
        try {
          pictureHtml = await galleryPictureHtml(content.imageSrc, fieldId, `bond-strengths-${id}.png`);
        } catch (error) {
          // The table still goes in without the picture.
          console.warn("The bond strengths picture could not be stored", error);
        }
      }

      // One undo step for the whole insert; it also marks the editor as changed.
      activeEditor.undoManager.transact(() => insertBondStrengthsAfter(chemImage, content, pictureHtml));
      activeEditor.nodeChanged();
    },
    [iframe],
  );

  useEffect(() => {
    const iframeDocument = getIframeDocument(iframe);
    if (!iframeDocument) {
      return undefined;
    }

    const handleChemClick = (event: MouseEvent): void => {
      const chemElement = isElementNode(event.target) ? event.target.closest("img.chem") : null;

      if (isChemImage(chemElement)) {
        addItem(chemElement);
      }
    };

    const handleChemInserted = (event: Event): void => {
      const chemId = getCustomEventDetail(event);
      if (typeof chemId !== "string" && typeof chemId !== "number") {
        return;
      }

      const chemElement = iframeDocument.getElementById(String(chemId));

      if (isChemImage(chemElement)) {
        addItem(chemElement);
      }
    };

    const handleChemUpdated = (): void => {
      closeAll();
    };

    const handleSidebarToggle = (event: Event): void => {
      const nextOpen = getCustomEventDetail(event);
      if (typeof nextOpen === "boolean") {
        setOpen(nextOpen);
      }
    };

    iframeDocument.addEventListener("click", handleChemClick);

    const observer = watchEditor(iframeDocument, (element) => {
      removeItem(element.id);
    });

    document.addEventListener("tinymce-chem-inserted", handleChemInserted);
    document.addEventListener("tinymce-chem-updated", handleChemUpdated);
    document.addEventListener("tinymce-chem-sidebar", handleSidebarToggle);

    return () => {
      iframeDocument.removeEventListener("click", handleChemClick);
      observer.disconnect();
      document.removeEventListener("tinymce-chem-inserted", handleChemInserted);
      document.removeEventListener("tinymce-chem-updated", handleChemUpdated);
      document.removeEventListener("tinymce-chem-sidebar", handleSidebarToggle);
    };
  }, [addItem, closeAll, iframe, removeItem]);

  useEffect(() => {
    const activeEditor = getActiveEditor();

    if (items.length === 1 && !open) {
      activeEditor?.execCommand("togglesidebar", false, "cheminfo");
      setOpen(true);
    } else if (items.length === 0 && open) {
      activeEditor?.execCommand("togglesidebar", false, "cheminfo");
      setOpen(false);
    }
  }, [items.length, open]);

  if (items.length === 0) {
    return null;
  }

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        overflowY: "auto",
        overflowX: "hidden",
        [`& .${paperClasses.root}`]: {
          overflow: "visible",
        },
      }}
    >
      {items.length > 1 && (
        <Box sx={{ textAlign: "right" }}>
          <Button sx={{ p: "10px" }} onClick={closeAll}>
            {t("tinymce.sidebarInfo.closeAll")}
          </Button>
        </Box>
      )}
      {items.map((item, idx) =>
        item.id ? (
          <ChemCard
            key={item.id}
            onClose={removeItem}
            onInsertBondStrengths={insertBondStrengths}
            item={item}
            idx={idx}
          />
        ) : null,
      )}
    </Box>
  );
}

function watchEditor(container: Document, callbackRemoved: (element: HTMLImageElement) => void): MutationObserver {
  const onMutationsObserved: MutationCallback = (mutations) => {
    mutations.forEach((mutation) => {
      mutation.removedNodes.forEach((removedNode) => {
        getRemovedChemElements(removedNode).forEach(callbackRemoved);
      });
    });
  };

  const observer = new MutationObserver(onMutationsObserved);
  observer.observe(container, { childList: true, subtree: true });
  return observer;
}
