import { render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import { useDeploymentProperty } from "../useDeploymentProperty";

function Consumer({ name, label = "value" }: { name: string; label?: string }) {
  const value = useDeploymentProperty(name);
  return <output aria-label={label}>{JSON.stringify(value)}</output>;
}

function countRequests(respond: (name: string | null) => Response) {
  const requested: Array<string | null> = [];
  server.use(
    http.get("/deploymentproperties/ajax/property", ({ request }) => {
      const name = new URL(request.url).searchParams.get("name");
      requested.push(name);
      return respond(name);
    }),
  );
  return requested;
}

describe("useDeploymentProperty", () => {
  test("components in separate React roots share one request", async () => {
    const requested = countRequests(() => HttpResponse.json(true));

    render(<Consumer name="netfilestores.enabled" label="first" />);
    render(<Consumer name="netfilestores.enabled" label="second" />);

    const success = JSON.stringify({ tag: "success", value: true });
    await waitFor(() => {
      expect(screen.getByRole("status", { name: "first" })).toHaveTextContent(success);
      expect(screen.getByRole("status", { name: "second" })).toHaveTextContent(success);
    });
    expect(requested).toEqual(["netfilestores.enabled"]);
  });

  test("a failed request reports the error and is retried on the next mount", async () => {
    let fail = true;
    const requested = countRequests(() =>
      fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json("ALLOWED"),
    );

    const { unmount } = render(<Consumer name="snapgene.available" />);
    expect(await screen.findByText(/"tag":"error"/)).toHaveTextContent(
      JSON.stringify({ tag: "error", error: "Request failed with status code 500" }),
    );
    unmount();

    fail = false;
    render(<Consumer name="snapgene.available" />);
    expect(await screen.findByText(/"tag":"success"/)).toHaveTextContent(
      JSON.stringify({ tag: "success", value: "ALLOWED" }),
    );
    expect(requested).toEqual(["snapgene.available", "snapgene.available"]);
  });

  test("changing the property name fetches the new property", async () => {
    const requested = countRequests((name) => HttpResponse.json(name === "aspose.enabled"));

    const { rerender } = render(<Consumer name="deployment.cloud" />);
    expect(await screen.findByText(/"tag":"success"/)).toHaveTextContent(
      JSON.stringify({ tag: "success", value: false }),
    );

    rerender(<Consumer name="aspose.enabled" />);
    expect(await screen.findByText(/"value":true/)).toHaveTextContent(JSON.stringify({ tag: "success", value: true }));
    expect(requested).toEqual(["deployment.cloud", "aspose.enabled"]);
  });
});
