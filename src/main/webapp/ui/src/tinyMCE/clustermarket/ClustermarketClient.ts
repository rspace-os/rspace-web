import axios, { type AxiosPromise } from "@/common/axios";
import { BookingType } from "./Enums";
import type {
  BOOKING_TYPE,
  BookingDetails,
  EquipmentDetails,
  BookingsList,
} from "./ClustermarketData";

// eslint-disable-next-line require-await
const getBookingsList = async (): AxiosPromise<BookingsList> => {
  return axios.get<BookingsList>("/apps/clustermarket/bookings");
};

const getBookingDetails = async (
  bookingIDs: string
  // eslint-disable-next-line require-await
): AxiosPromise<Array<BookingDetails>> => {
  return axios.put("/apps/clustermarket/bookings/details", {
    bookingIDs,
  });
};

const getEquipmentDetails = async (
  equipmentIDs: string
  // eslint-disable-next-line require-await
): AxiosPromise<Array<EquipmentDetails>> => {
  return axios.put("/apps/clustermarket/equipment/details", { equipmentIDs });
};

export const getRelevantBookings = (
  bookingType: (typeof BookingType)[keyof typeof BookingType],
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
  bookingType: (typeof BookingType)[keyof typeof BookingType]
): Promise<BookingsList> => {
  const bookingsList = (await getBookingsList()).data;
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
): Array<number> => {
  const equipmentIDsMap = new Set<number>();
  bookingsList.map((booking) => equipmentIDsMap.add(booking.equipment_id));
  return Array.from(equipmentIDsMap);
};

export const getAllEquipmentDetails = async (
  bookingsList: BookingsList
): Promise<Array<EquipmentDetails>> => {
  const equipmentIDs = getUniqueEquipmentIdsFromBookings(bookingsList);
  const equipmentIDsString = equipmentIDs.map((id) => id).join();
  return (await getEquipmentDetails(equipmentIDsString)).data;
};
