import type { Page } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";

interface SnippetCreateResponse {
  data: { key: string; arguments: unknown[] } | null;
  errorMsg: { key: string; arguments: unknown[] } | null;
}

export class SnippetsClient {
  constructor(private readonly page: Page) {}

  async createFromContent({ name, content }: { name: string; content: string }): Promise<void> {
    const res = await this.page.request.post("/snippet/create", {
      headers: { Referer: env.baseURL },
      form: { snippetName: name, content, fieldId: "0" },
    });
    if (!res.ok()) {
      throw new Error(`POST /snippet/create failed: ${res.status()} ${res.statusText()}`);
    }
    const body = (await res.json()) as SnippetCreateResponse;
    if (body.errorMsg) {
      throw new Error(`Snippet creation failed: ${body.errorMsg.key}`);
    }
  }
}
