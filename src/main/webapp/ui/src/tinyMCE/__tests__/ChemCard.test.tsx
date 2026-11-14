import { act, render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw/http";
import { expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import axios from "@/common/axios";
import ChemCard from "@/tinyMCE/ChemCard";

test("an obsolete response cannot replace the latest chemistry properties", async () => {
  const releaseOldResponse = Promise.withResolvers<void>();
  const oldResponseReceived = Promise.withResolvers<void>();
  let requests = 0;
  let responses = 0;
  server.use(
    http.get("/chemical/ajax/getInfo", async () => {
      const obsolete = ++requests === 1;
      if (obsolete) await releaseOldResponse.promise;
      return HttpResponse.json({
        data: {
          reactants: [],
          products: [],
          molecules: [{ name: obsolete ? "Methane" : "Water", formula: obsolete ? "CH4" : "H2O" }],
        },
      });
    }),
  );
  const responseObserver = axios.interceptors.response.use((response) => {
    if (++responses === 2) oldResponseReceived.resolve();
    return response;
  });

  try {
    const { rerender } = render(<ChemCard item={{ id: "991001", src: "/preview?time=1" }} inline height={200} />);
    await waitFor(() => expect(requests).toBe(1));
    rerender(<ChemCard item={{ id: "991001", src: "/preview?time=2" }} inline height={200} />);
    expect(await screen.findByRole("cell", { name: "Water" })).toBeVisible();

    await act(async () => {
      releaseOldResponse.resolve();
      await oldResponseReceived.promise;
    });

    expect(screen.getByRole("cell", { name: "Water" })).toBeVisible();
    expect(screen.queryByRole("cell", { name: "Methane" })).not.toBeInTheDocument();
  } finally {
    releaseOldResponse.resolve();
    axios.interceptors.response.eject(responseObserver);
  }
});
