import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { beforeEach, describe, expect, test, vi } from "vitest";
import axios from "@/common/axios";
import Clustermarket, { getOrder, getOrderBy } from "../Clustermarket";
import { BookingType, Order } from "../Enums";
import BookingsList from "./allbookings.json";
import BookingDetails from "./bookingsDetails.json";
import EquipmentDetails from "./equipmentDetails.json";

const mockAxios = new MockAdapter(axios);
const localStorageMock = {
  getItem: vi.fn(),
};
const rsMock = {
  trackEvent: vi.fn(),
};
Object.defineProperty(window, "localStorage", { value: localStorageMock });
Object.defineProperty(window, "RS", { value: rsMock });
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
const getWrapper = (props?: any) => {
  return render(<Clustermarket {...props} />);
};
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
const findFirstByText = async (text: any, options?: any, waitOptions?: any) => {
  const [match] = await screen.findAllByText(text, options, waitOptions);
  return match;
};
beforeEach(() => {
  mockAxios.onGet("/apps/clustermarket/bookings").reply(200, BookingsList.data);
  mockAxios
    .onPut("/apps/clustermarket/bookings/details", {
      bookingIDs: "COMPLETED_1,CURRENT_2,COMPLETED_3,COMPLETED_4,CURRENT_5",
    })
    .reply(200, [
      BookingDetails.COMPLETED_1,
      BookingDetails.CURRENT_2,
      BookingDetails.COMPLETED_3,
      BookingDetails.COMPLETED_4,
      BookingDetails.CURRENT_5,
    ]);
  mockAxios
    .onPut("/apps/clustermarket/bookings/details", {
      bookingIDs: "CURRENT_2,CURRENT_5",
    })
    .reply(200, [BookingDetails.CURRENT_2, BookingDetails.CURRENT_5]);
  mockAxios
    .onPut("/apps/clustermarket/equipment/details", { equipmentIDs: "1,2,3" })
    .reply(200, [EquipmentDetails["1"], EquipmentDetails["2"], EquipmentDetails["3"]]);
  mockAxios
    .onPut("/apps/clustermarket/equipment/details", { equipmentIDs: "2,3" })
    .reply(200, [EquipmentDetails["2"], EquipmentDetails["3"]]);
});
describe("Has defaultOrderBy", () => {
  test("when no value in localStorage then returns Order by start_time", () => {
    expect(getOrderBy()).toEqual("start_time");
  });
  test("returns Order By value in localStorage", () => {
    localStorageMock.getItem = vi.fn().mockImplementationOnce(() => '"duration"');
    expect(getOrderBy()).toEqual("duration");
  });
});

describe("Has defaultOrder", () => {
  test("when no value in localStorage then returns  Order.asc", () => {
    expect(getOrder()).toEqual(Order.asc);
  });
  test("returns Order value in localStorage", () => {
    localStorageMock.getItem = vi.fn().mockImplementationOnce(() => Order.desc);
    expect(getOrder()).toEqual(Order.desc);
  });
});

describe("Renders page with booking data", () => {
  test("displays booking table headers", async () => {
    getWrapper();
    await findFirstByText("workspace:tinymce.clustermarket.columns.bookingId");
  });

  test("displays booking type radio and maintenance checkbox", async () => {
    getWrapper();
    await findFirstByText("workspace:tinymce.clustermarket.bookingTypes.booked");
    await findFirstByText("workspace:tinymce.clustermarket.bookingTypes.all");
    await findFirstByText("workspace:tinymce.clustermarket.bookingTypes.equipment");
    await findFirstByText("workspace:tinymce.clustermarket.maintenanceOnly");
  });

  test('displays bookings of type "booked"', async () => {
    getWrapper();
    await findFirstByText("workspace:tinymce.clustermarket.columns.bookingId");
    await findFirstByText("CURRENT_2");
    expect(screen.getByText("CURRENT_2")).toBeInTheDocument();
    expect(screen.getByTestId("status0")).toHaveTextContent("Booked");
    expect(screen.getByTestId("status1")).toHaveTextContent("Booked");
    expect(screen.getByText("CURRENT_5")).toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_1")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_4")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_3")).not.toBeInTheDocument();
  });

  test('displays bookings of type "booked and completed"', async () => {
    getWrapper({ defaultBookingType: BookingType.ALL });
    await findFirstByText("workspace:tinymce.clustermarket.columns.bookingId");
    screen.getByText("CURRENT_2");
    expect(screen.getByText("CURRENT_2")).toBeInTheDocument();
    expect(screen.getByText("COMPLETED_3")).toBeInTheDocument();
    expect(screen.getByText("COMPLETED_1")).toBeInTheDocument();
  });

  test("bookings are ordered by start date, ascending", async () => {
    getWrapper({ defaultBookingType: BookingType.ALL });

    await findFirstByText("workspace:tinymce.clustermarket.columns.bookingId");
    await findFirstByText("2022-01-28 07:30:00");
    const startTimes = screen.getAllByTestId(/start_time/).map((cell) => cell.textContent);
    expect(startTimes).toEqual(
      expect.arrayContaining([
        "2022-01-28 07:30:00",
        "2022-02-28 06:30:00",
        "2022-02-28 06:31:00",
        "2022-02-28 07:30:00",
        "2022-02-28 07:31:00",
      ]),
    );
  });

  test("displays headers with no results table if no data and ALL bookings", async () => {
    mockAxios.resetHandlers();
    mockAxios.onGet("/apps/clustermarket/bookings").reply(200, []);
    getWrapper({ defaultBookingType: BookingType.ALL });
    await findFirstByText("workspace:tinymce.clustermarket.columns.bookingId");
    expect(screen.queryAllByText("CURRENT_2")).toHaveLength(0);
    expect(screen.queryByText("COMPLETED_3")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_1")).not.toBeInTheDocument();
  });

  test("displays headers with no results table if no data and Booked bookings", async () => {
    mockAxios.resetHandlers();
    mockAxios.onGet("/apps/clustermarket/bookings").reply(200, []);
    getWrapper();
    await findFirstByText("workspace:tinymce.clustermarket.columns.bookingId");
    expect(screen.queryAllByText("CURRENT_2")).toHaveLength(0);
    expect(screen.queryByText("COMPLETED_3")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_1")).not.toBeInTheDocument();
  });

  test("adds noreferrer to links in inserted tables", async () => {
    const originalTinymce = Object.getOwnPropertyDescriptor(window, "tinymce");
    const handlers = new Map<string, () => void>();
    const editor = {
      execCommand: vi.fn(),
      off: vi.fn((event: string) => handlers.delete(event)),
      on: vi.fn((event: string, handler: () => void) => handlers.set(event, handler)),
      windowManager: { close: vi.fn() },
    };
    Object.defineProperty(window, "tinymce", {
      configurable: true,
      value: { activeEditor: editor },
    });

    try {
      const user = userEvent.setup();
      getWrapper({ clustermarket_web_url: "https://calira.example/" });
      const bookingId = await findFirstByText("CURRENT_2");
      const bookingRow = bookingId.closest("tr");
      expect(bookingRow).not.toBeNull();
      if (!bookingRow) throw new Error("Booking row is missing");

      await user.click(bookingRow);
      expect(bookingRow).toBeChecked();

      handlers.get("clustermarket-insert")?.();

      expect(editor.execCommand).toHaveBeenCalledOnce();
      const insertedHtml = editor.execCommand.mock.calls[0]?.[2] as string;
      const container = document.createElement("div");
      container.innerHTML = insertedHtml;
      const links = Array.from(container.querySelectorAll("a"));

      expect(links).toHaveLength(2);
      links.forEach((link) => {
        expect(link).toHaveAttribute("target", "_blank");
        expect(link).toHaveAttribute("rel", "noreferrer");
      });
    } finally {
      if (originalTinymce) {
        Object.defineProperty(window, "tinymce", originalTinymce);
      } else {
        Reflect.deleteProperty(window, "tinymce");
      }
    }
  });
});
