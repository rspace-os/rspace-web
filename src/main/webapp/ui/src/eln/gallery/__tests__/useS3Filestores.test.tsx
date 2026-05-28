import { test, describe, expect, beforeEach, vi, afterEach } from "vitest";
import "@/__tests__/__mocks__/useOauthToken";
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";

import axios from "@/common/axios";
import useS3Filestores from "../components/useS3Filestores";
import Alerts from "../../../components/Alerts/Alerts";

// The hook calls axios.create(...) and uses the returned instance, but
// axios-mock-adapter only intercepts the instance it was constructed against.
// Stub axios.create so it returns the default instance, which has the mock
// adapter attached.

function FilestoreList() {
  const result = useS3Filestores();
  if (result.tag === "loading") return <div data-testid="state">loading</div>;
  if (result.tag === "error")
    return <div data-testid="state">error:{result.error}</div>;
  return (
    <ul data-testid="filestores">
      {result.value.map((fs) => (
        <li key={fs.id} data-testid={`fs-${fs.id}`}>
          {fs.name}|canRead={String(fs.canRead)}|canWrite={String(fs.canWrite)}
        </li>
      ))}
    </ul>
  );
}

describe("useS3Filestores", () => {
  let mockAxios: MockAdapter;

  beforeEach(() => {
    mockAxios = new MockAdapter(axios);
    vi.spyOn(axios, "create").mockReturnValue(
      axios as unknown as ReturnType<typeof axios.create>,
    );
  });

  afterEach(() => {
    mockAxios.restore();
    vi.restoreAllMocks();
  });

  test("populates canRead/canWrite from userPermissions when present", async () => {
    // after the axios.create stub, baseURL is ignored, so the path is just "/filestores"
    mockAxios.onGet("/filestores").reply(200, [
      {
        id: 1,
        name: "writable-s3",
        fileSystem: { clientType: "S3" },
        userPermissions: { canRead: true, canWrite: true },
      },
      {
        id: 2,
        name: "read-only-s3",
        fileSystem: { clientType: "S3" },
        userPermissions: { canRead: true, canWrite: false },
      },
    ]);

    render(
      <Alerts>
        <FilestoreList />
      </Alerts>,
    );

    await waitFor(() => screen.getByTestId("filestores"));
    expect(screen.getByTestId("fs-1")).toHaveTextContent(
      "writable-s3|canRead=true|canWrite=true",
    );
    expect(screen.getByTestId("fs-2")).toHaveTextContent(
      "read-only-s3|canRead=true|canWrite=false",
    );
  });

  test("stale filestore (canRead=false) is still listed", async () => {
    // a filestore the user has lost access to since binding remains visible
    // so the UI can render it as inaccessible rather than silently dropping it
    // after the axios.create stub, baseURL is ignored, so the path is just "/filestores"
    mockAxios.onGet("/filestores").reply(200, [
      {
        id: 3,
        name: "stale-s3",
        fileSystem: { clientType: "S3" },
        userPermissions: { canRead: false, canWrite: false },
      },
    ]);

    render(
      <Alerts>
        <FilestoreList />
      </Alerts>,
    );

    await waitFor(() => screen.getByTestId("filestores"));
    expect(screen.getByTestId("fs-3")).toHaveTextContent(
      "stale-s3|canRead=false|canWrite=false",
    );
  });

  test("defaults canRead/canWrite to true when userPermissions absent", async () => {
    // older backend or non-NONE auth filesystem omits the permissions snapshot;
    // the UI should remain permissive in that case rather than blocking the user
    // after the axios.create stub, baseURL is ignored, so the path is just "/filestores"
    mockAxios.onGet("/filestores").reply(200, [
      {
        id: 4,
        name: "legacy-s3",
        fileSystem: { clientType: "S3" },
      },
    ]);

    render(
      <Alerts>
        <FilestoreList />
      </Alerts>,
    );

    await waitFor(() => screen.getByTestId("filestores"));
    expect(screen.getByTestId("fs-4")).toHaveTextContent(
      "legacy-s3|canRead=true|canWrite=true",
    );
  });

  test("non-S3 filestores are filtered out of the list", async () => {
    // after the axios.create stub, baseURL is ignored, so the path is just "/filestores"
    mockAxios.onGet("/filestores").reply(200, [
      {
        id: 5,
        name: "an-s3",
        fileSystem: { clientType: "S3" },
        userPermissions: { canRead: true, canWrite: true },
      },
      {
        id: 6,
        name: "an-irods",
        fileSystem: { clientType: "IRODS" },
        userPermissions: { canRead: true, canWrite: true },
      },
    ]);

    render(
      <Alerts>
        <FilestoreList />
      </Alerts>,
    );

    await waitFor(() => screen.getByTestId("filestores"));
    expect(screen.queryByTestId("fs-5")).not.toBeNull();
    expect(screen.queryByTestId("fs-6")).toBeNull();
  });
});
