/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import Clustermarket, { getOrder, getOrderBy } from "../Clustermarket";
import React from "react";
import * as axios from "axios";
import { render, screen, act } from "@testing-library/react";
import "@testing-library/jest-dom";
import MockAdapter from "axios-mock-adapter";
import BookingDetails from "./bookingsDetails.json";
import EquipmentDetails from "./equipmentDetails.json";
import BookingsList from "./allbookings.json";
import { BookingType, Order } from "../Enums";
const mockAxios = new MockAdapter(axios);
const localStorageMock = {
  getItem: jest.fn(),
};
const rsMock = {
  trackEvent: jest.fn(),
};
Object.defineProperty(window, "localStorage", { value: localStorageMock });
Object.defineProperty(window, "RS", { value: rsMock });
const getWrapper = (props) => {
  return render(<Clustermarket {...props} />);
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
describe("Has defaultOrderBy ", () => {
  it("when no value in localStorage then returns Order by start_time", () => {
    expect(getOrderBy()).toEqual("start_time");
  });
  it("returns Order By value in localStorage", () => {
    localStorageMock.getItem = jest
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
    localStorageMock.getItem = jest
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
      return screen.findByText("Booking ID");
    });
  });

  it("displays booking type radio and maintenance checkbox", async () => {
    getWrapper();
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return screen.findByText("Booked");
    });
    await screen.findByText("Booked and Completed");
    await screen.findByText("Booked Equipment");
    await screen.findByText("maintenance only");
  });

  it('displays bookings of type "booked"', async () => {
    getWrapper();
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return screen.findByText("Booking ID");
    });
    await screen.findByText("CURRENT_2");
    expect(screen.getByText("CURRENT_2")).toBeInTheDocument();
    expect(screen.getByTestId("status0")).toHaveTextContent("Booked");
    expect(screen.getByTestId("status1")).toHaveTextContent("Booked");
    expect(screen.getByText("CURRENT_5")).toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_1")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_4")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_3")).not.toBeInTheDocument();
  });

  it('displays bookings of type "booked and completed"', async () => {
    getWrapper({ defaultBookingType: BookingType.ALL });
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return screen.findByText("Booking ID");
    });
    screen.getByText("CURRENT_2");
    expect(screen.getByText("CURRENT_2")).toBeInTheDocument();
    expect(screen.getByText("COMPLETED_3")).toBeInTheDocument();
    expect(screen.getByText("COMPLETED_1")).toBeInTheDocument();
  });

  it("bookings are ordered by start date, ascending", async () => {
    getWrapper({ defaultBookingType: BookingType.ALL });
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return screen.findByText("Booking ID");
    });

    await screen.findByText("2022-01-28 07:30:00");
    expect(screen.getByTestId("start_time0")).toHaveTextContent(
      "2022-01-28 07:30:00"
    );

    expect(screen.getByTestId("start_time1")).toHaveTextContent(
      "2022-02-28 06:30:00"
    );

    expect(screen.getByTestId("start_time2")).toHaveTextContent(
      "2022-02-28 06:31:00"
    );

    expect(screen.getByTestId("start_time3")).toHaveTextContent(
      "2022-02-28 07:30:00"
    );
  });

  it("displays headers with no results table if no data and ALL bookings ", async () => {
    mockAxios.onGet("/apps/clustermarket/bookings").reply(200, []);
    getWrapper({ defaultBookingType: BookingType.ALL });
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return screen.findByText("Booking ID");
    });
    expect(screen.queryByText("CURRENT_2")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_3")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_1")).not.toBeInTheDocument();
  });

  it("displays headers with no results table if no data and Booked bookings ", async () => {
    mockAxios.onGet("/apps/clustermarket/bookings").reply(200, []);
    getWrapper();
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(() => {
      return screen.findByText("Booking ID");
    });
    expect(screen.queryByText("CURRENT_2")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_3")).not.toBeInTheDocument();
    expect(screen.queryByText("COMPLETED_1")).not.toBeInTheDocument();
  });
});
