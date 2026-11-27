import { describe, expect, test } from "vitest";
import { applyCspNonce, getCspNonce } from "../csp";

function createDocument(attribute: "name" | "property", nonce: string): Document {
  const targetDocument = document.implementation.createHTMLDocument();
  const meta = targetDocument.createElement("meta");
  meta.setAttribute(attribute, "csp-nonce");
  if (attribute === "property") {
    meta.nonce = nonce;
  } else {
    meta.content = nonce;
  }
  targetDocument.head.appendChild(meta);
  return targetDocument;
}

describe("CSP nonce helpers", () => {
  test("reads the Vite nonce and applies it to dynamic scripts", () => {
    const targetDocument = createDocument("property", "vite-nonce");
    const script = targetDocument.createElement("script");

    applyCspNonce(script);

    expect(getCspNonce(targetDocument)).toBe("vite-nonce");
    expect(script.nonce).toBe("vite-nonce");
  });

  test("supports a content nonce for documents that do not use Vite", () => {
    const targetDocument = createDocument("name", "content-nonce");

    expect(getCspNonce(targetDocument)).toBe("content-nonce");
  });
});
