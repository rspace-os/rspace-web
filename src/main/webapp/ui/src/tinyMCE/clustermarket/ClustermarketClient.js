// @flow
import axios, { type AxiosPromise } from "@/common/axios";
import { BookingType } from "./Enums";
import type {
  BOOKING_TYPE,
  BookingDetails,
  EquipmentDetails,
  BookingsList,
} from "./ClustermarketData";

// eslint-disable-next-line require-await
const getBookingsList = async (): AxiosPromise<mixed, BookingsList> => {
  return axios.get("/apps/clustermarket/bookings");
};

const getBookingDetails = async (
  bookingIDs: string
  // eslint-disable-next-line require-await
): AxiosPromise<mixed, Array<BookingDetails>> => {
  return axios.put("/apps/clustermarket/bookings/details", {
    bookingIDs,
  });
};

const getEquipmentDetails = async (
  equipmentIDs: string
  // eslint-disable-next-line require-await
): AxiosPromise<mixed, Array<EquipmentDetails>> => {
  return axios.put("/apps/clustermarket/equipment/details", { equipmentIDs });
};

export const getRelevantBookings = (
  bookingType: BOOKING_TYPE,
  bookingsList: BookingsList
): BookingsList => {
  let relevantBookings: BookingsList;
  if (bookingType === BookingType.BOOKED) {
    relevantBookings = bookingsList.filter(
      (booking) => booking.status.toLowerCase() === bookingType.toLowerCase()
    );
  } else {
    relevantBookings = bookingsList.filter(
      (booking) => booking.status.toLowerCase() !== "declined"
    );
  }
  return relevantBookings;
};

export const getBookings = async (
  bookingType: BOOKING_TYPE
): Promise<BookingsList> => {
  const bookingsList = ((await getBookingsList()).data: BookingsList);
  if (bookingsList) {
    return getRelevantBookings(bookingType, bookingsList);
  }
  return [];
};

export const getAllBookingDetails = async (
  bookingsList: BookingsList
): Promise<Array<BookingDetails>> => {
  const bookingIdsString = bookingsList.map((booking) => booking.id).join();
  return (await getBookingDetails(bookingIdsString)).data;
};

export const getUniqueEquipmentIdsFromBookings = (
  bookingsList: BookingsList
): Array<string> => {
  const equipmentIDsMap = new Map<string, mixed>();
  bookingsList.map((booking) => equipmentIDsMap.set(booking.equipment_id));
  return Array.from(equipmentIDsMap.keys());
};

export const getAllEquipmentDetails = async (
  bookingsList: BookingsList
): Promise<Array<EquipmentDetails>> => {
  const equipmentIDs = getUniqueEquipmentIdsFromBookings(bookingsList);
  const equipmentIDsString = equipmentIDs.map((id) => id).join();
  return (await getEquipmentDetails(equipmentIDsString)).data;
};
