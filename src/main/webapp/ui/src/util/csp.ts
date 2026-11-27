export function getCspNonce(ownerDocument: Document = document): string | undefined {
  const viteNonce = ownerDocument.querySelector<HTMLMetaElement>('meta[property="csp-nonce"]')?.nonce;
  if (viteNonce) {
    return viteNonce;
  }

  return ownerDocument.querySelector<HTMLMetaElement>('meta[name="csp-nonce"]')?.content || undefined;
}

export function applyCspNonce(element: HTMLScriptElement | HTMLStyleElement): void {
  const nonce = getCspNonce(element.ownerDocument);
  if (nonce) {
    element.nonce = nonce;
  }
}
