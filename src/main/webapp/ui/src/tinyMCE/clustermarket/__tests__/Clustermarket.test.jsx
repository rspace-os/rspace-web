/*
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import Clustermarket, { getOrder, getOrderBy } from "../Clustermarket";
import React from "react";
import axios from "@/common/axios";
import { render, screen, act, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import MockAdapter from "axios-mock-adapter";
import BookingDetails from "./bookingsDetails.json";
import EquipmentDetails from "./equipmentDetails.json";
import BookingsList from "./allbookings.json";
import { BookingType, Order } from "../Enums";
const mockAxios = new MockAdapter(axios);
const localStorageMock = {
  getItem: vi.fn(),
};
const rsMock = {
  trackEvent: vi.fn(),
};
Object.defineProperty(window, "localStorage", { value: localStorageMock });
Object.defineProperty(window, "RS", { value: rsMock });
const getWrapper = (props) => {
  return render(<Clustermarket {...props} />);
};
const findFirstByText = async (text, options, waitOptions) => {
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
    .reply(200, [
      EquipmentDetails["1"],
      EquipmentDetails["2"],
      EquipmentDetails["3"],
    ]);
  mockAxios
    .onPut("/apps/clustermarket/equipment/details", { equipmentIDs: "2,3" })
    .reply(200, [EquipmentDetails["2"], EquipmentDetails["3"]]);
});

afterEach(cleanup);
describe("Has defaultOrderBy ", () => {
  it("when no value in localStorage then returns Order by start_time", () => {
    expect(getOrderBy()).toEqual("start_time");
  });
  it("returns Order By value in localStorage", () => {
    localStorageMock.getItem = vi
      .fn()
      .mockImplementationOnce(() => '"duration"');
    expect(getOrderBy()).toEqual("duration");
  });
});

describe("Has defaultOrder ", () => {
  it("when no value in localStorage then returns  Order.asc", () => {
    expect(getOrder()).toEqual(Order.asc);
  });
  it("returns Order value in localStorage", () => {
    localStorageMock.getItem = vi
      .fn()
      .mockImplementationOnce(() => Order.desc);
    expect(getOrder()).toEqual(Order.desc);
  });
});

describe("Renders page with booking data ", () => {
  it("displays booking table headers", async () => {
    getWrapper();
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return findFirstByText("Booking ID");
    });
  });

  it("displays booking type radio and maintenance checkbox", async () => {
    getWrapper();
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return findFirstByText("Booked");
    });
    await findFirstByText("Booked and Completed");
    await findFirstByText("Booked Equipment");
    await findFirstByText("maintenance only");
  });

  it('displays bookings of type "booked"', async () => {
    getWrapper();
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return findFirstByText("Booking ID");
    });
    await findFirstByText("CURRENT_2");
    expect(screen.getAllByText("CURRENT_2")[0]).toBeInTheDocument();
    expect(screen.getAllByTestId("status0")[0]).toHaveTextContent("Booked");
    expect(screen.getAllByTestId("status1")[0]).toHaveTextContent("Booked");
    expect(screen.getAllByText("CURRENT_5")[0]).toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_1")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_4")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_3")).not.toBeInTheDocument();
  });

  it('displays bookings of type "booked and completed"', async () => {
    getWrapper({ defaultBookingType: BookingType.ALL });
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return findFirstByText("Booking ID");
    });
    screen.getAllByText("CURRENT_2")[0];
    expect(screen.getAllByText("CURRENT_2")[0]).toBeInTheDocument();
    expect(screen.getAllByText("COMPLETED_3")[0]).toBeInTheDocument();
    expect(screen.getAllByText("COMPLETED_1")[0]).toBeInTheDocument();
  });

  it("bookings are ordered by start date, ascending", async () => {
    getWrapper({ defaultBookingType: BookingType.ALL });
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return findFirstByText("Booking ID");
    });

    await findFirstByText("2022-01-28 07:30:00");
    const startTimes = screen
      .getAllByTestId(/start_time/)
      .map((cell) => cell.textContent);
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

  it("displays headers with no results table if no data and ALL bookings ", async () => {
    mockAxios.resetHandlers();
    mockAxios.onGet("/apps/clustermarket/bookings").reply(200, []);
    getWrapper({ defaultBookingType: BookingType.ALL });
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return findFirstByText("Booking ID");
    });
    expect(screen.queryAllByText("CURRENT_2")).toHaveLength(0);
    expect(screen.queryByText("COMPLETED_3")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_1")).not.toBeInTheDocument();
  });

  it("displays headers with no results table if no data and Booked bookings ", async () => {
    mockAxios.resetHandlers();
    mockAxios.onGet("/apps/clustermarket/bookings").reply(200, []);
    getWrapper();
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return findFirstByText("Booking ID");
    });
    expect(screen.queryAllByText("CURRENT_2")).toHaveLength(0);
    expect(screen.queryByText("COMPLETED_3")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_1")).not.toBeInTheDocument();
  });
});
