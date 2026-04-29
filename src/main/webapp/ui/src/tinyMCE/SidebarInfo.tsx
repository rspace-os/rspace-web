import React, { useCallback, useEffect } from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import ChemCard from "./ChemCard";
import styled from "@emotion/styled";
import { createRoot, type Root } from "react-dom/client";
import materialTheme from "../theme";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";

interface SidebarItem extends Record<string, string | undefined> {
  id: string;
  imageSrc?: string;
}

interface SidebarInfoProps {
  iframe: HTMLIFrameElement;
}

type TinyMCEEditor = {
  execCommand: (command: string, ui: boolean, value?: string) => void;
};

const sidebarRoots = new WeakMap<HTMLElement, Root>();

const SidebarWrapper = styled.div`
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;

  .MuiPaper-root {
    overflow: visible;
  }
`;

function isElementNode(node: EventTarget | Node | null): node is Element {
  return (
    typeof node === "object" &&
    node !== null &&
    "nodeType" in node &&
    node.nodeType === Node.ELEMENT_NODE
  );
}

function isChemImage(element: Element | null): element is HTMLImageElement {
  return (
    element?.tagName.toLowerCase() === "img" &&
    element.classList.contains("chem")
  );
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
    removedChemElements.push(
      ...Array.from(node.querySelectorAll<HTMLImageElement>("img.chem")),
    );
  }

  return removedChemElements;
}

function getCustomEventDetail(event: Event): unknown {
  return "detail" in event ? event.detail : undefined;
}

function getActiveEditor(): TinyMCEEditor | undefined {
  return (globalThis as { tinymce?: { activeEditor?: TinyMCEEditor } }).tinymce
    ?.activeEditor;
}

function findSidebarContainer(iframe: HTMLIFrameElement): HTMLElement | null {
  const editorContainer = iframe.closest(".tox-tinymce");
  const container =
    editorContainer?.querySelector<HTMLElement>(".tox-sidebar__pane-container") ??
    document.querySelector<HTMLElement>(".tox-sidebar__pane-container");

  return container instanceof HTMLElement ? container : null;
}

export default function SidebarInfo({ iframe }: SidebarInfoProps) {
  const [items, setItems] = React.useState<SidebarItem[]>([]);
  const [open, setOpen] = React.useState(false);

  const addItem = useCallback((element: HTMLImageElement): void => {
    if (!element.id) {
      return;
    }

    const item: SidebarItem = {
      id: element.id,
      imageSrc: element.getAttribute("src") ?? undefined,
    };

    setItems((oldItems) => [
      item,
      ...oldItems.filter((existingItem) => existingItem.id !== item.id),
    ]);
  }, []);

  const removeItem = useCallback((id?: string): void => {
    setItems((oldItems) => oldItems.filter((item) => item.id !== id));
  }, []);

  const closeAll = useCallback(
    (event?: React.MouseEvent<HTMLButtonElement>): void => {
      event?.preventDefault();
      setItems([]);
    },
    [],
  );

  useEffect(() => {
    const iframeDocument = getIframeDocument(iframe);
    if (!iframeDocument) {
      return undefined;
    }

    const handleChemClick = (event: MouseEvent): void => {
      const chemElement =
        isElementNode(event.target)
          ? event.target.closest("img.chem")
          : null;

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
    <SidebarWrapper>
      {items.length > 1 && (
        <Box sx={{ textAlign: "right" }}>
          <Button sx={{ p: "10px" }} onClick={closeAll}>
            Close All
          </Button>
        </Box>
      )}
      {items.map((item, idx) =>
        item.id ? (
          <ChemCard key={item.id} onClose={removeItem} item={item} idx={idx} />
        ) : null,
      )}
    </SidebarWrapper>
  );
}

function SidebarInfoEntrypoint(props: SidebarInfoProps) {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <SidebarInfo {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

document.addEventListener("tinymce-iframe-loaded", (event: Event) => {
  const iframeSelector = getCustomEventDetail(event);
  if (typeof iframeSelector !== "string") {
    return;
  }

  const iframe = document.querySelector(iframeSelector);
  const container =
    iframe instanceof HTMLIFrameElement ? findSidebarContainer(iframe) : null;

  if (!(iframe instanceof HTMLIFrameElement) || !(container instanceof HTMLElement)) {
    return;
  }

  const root = sidebarRoots.get(container) ?? createRoot(container);
  sidebarRoots.set(container, root);
  root.render(<SidebarInfoEntrypoint iframe={iframe} />);
});

function watchEditor(
  container: Document,
  callbackRemoved: (element: HTMLImageElement) => void,
): MutationObserver {
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
