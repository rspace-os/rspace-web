// @flow
//booking centric view
import { BookingType } from "./Enums";
import * as ArrayUtils from "../../util/ArrayUtils";

export type BOOKING_TYPE = typeof BookingType;
export type Requester = { name: string };
export type Equipment = { name: string, id: number };
export type RequesterLab = { id: number, name: String };
export type BookingsList = Array<{
  id: number,
  start_time: string,
  end_time: string,
  equipment_id: string,
  status: string,
}>;

export type Note = {
  message: string,
};

export type BookingDetails = {
  id: string,
  requester: Requester,
  equipment: Equipment,
  booking_type: string,
  duration: number,
  requester_lab: RequesterLab,
  last_public_note: Note | null,
};

export type EquipmentDetails = {
  name: string,
  manufacturer: string,
  model: string,
  id: number,
};
const EquipmentDetailsNotFound: EquipmentDetails = {
  name: "",
  manufacturer: "",
  model: "",
  id: -1,
};

export type BookingAndEquipmentDetails = {
  bookingID: string,
  requesterName: string,
  bookingType: string,
  duration: number,
  status: string,
  equipmentName: string,
  manufacturer: string,
  model: string,
  equipmentID: number,
  labID: number,
};

export type EquipmentWithBookingDetails = {
  equipmentID: string,
  equipmentName: string,
  manufacturer: string,
  model: string,
  maintenance_notes?: string,
  bookingID: string,
  start_time: string,
};

export const replaceNullWithEmptyString = (data: { ... }): void => {
  for (const [key] of Object.entries(data)) {
    if (data[key] === null) {
      data[key] = "";
    }
  }
};

function getRelevantForMaintenanceType(
  isMaintenance: boolean,
  bookingDetails: Array<BookingDetails>
) {
  return isMaintenance
    ? bookingDetails.filter(
        (bookingDetail) => bookingDetail.booking_type === "maintenance"
      )
    : bookingDetails;
}

const getEquipment = (
  id: number,
  equipmentDetails: Array<EquipmentDetails>
): EquipmentDetails => {
  let find = equipmentDetails.find(
    (equipmentDetail) => equipmentDetail.id === id
  );
  if (!find) {
    find = EquipmentDetailsNotFound;
  }
  return find;
};

const formatDate = (date: string) => {
  const dateTime = date.split("T");
  const roundedTime = dateTime[1].split(".");
  return dateTime[0] + " " + roundedTime[0];
};

const getBookingSummary = (id: string, bookingsList: BookingsList) => {
  return ArrayUtils.find((item) => item.id === id, bookingsList).orElseGet(
    () => {
      throw new Error(`There is no booking with the id ${id}.`);
    }
  );
};

const getStartTime = (id: string, bookingsList: BookingsList): string => {
  return formatDate(getBookingSummary(id, bookingsList).start_time);
};

const getStatus = (id: string, bookingsList: BookingsList): string => {
  return getBookingSummary(id, bookingsList).status;
};

const getEndTime = (id: string, bookingsList: BookingsList): string => {
  return formatDate(getBookingSummary(id, bookingsList).end_time);
};

export const getMostRecentCompletedBooking = (
  relevantBookings: Array<BookingDetails>,
  bookingList: BookingsList,
  equipmentID: number
): ?BookingDetails => {
  const matching = relevantBookings
    .filter(
      (bookingDetail: BookingDetails) =>
        bookingDetail.equipment.id === equipmentID
    )
    .filter(
      (bookingDetail: BookingDetails) =>
        getStatus(bookingDetail.id, bookingList).toLowerCase() === "completed"
    );
  if (matching.length > 1) {
    return matching.sort((first, second) => {
      const firstStart = getStartTime(first.id, bookingList);
      const secondStart = getStartTime(second.id, bookingList);
      if (secondStart > firstStart) {
        return 1;
      }
      if (firstStart === secondStart) {
        return 0;
      }
      return -1;
    })[0];
  } else if (matching.length > 0) {
    return matching[0];
  }
  return null;
};

const getALabID = (bookingDetails: Array<BookingDetails>): number => {
  return bookingDetails.find(
    (booking: BookingDetails) => typeof booking.requester_lab.id !== "undefined"
    // $FlowExpectedError[incompatible-use] `find` is certain to find a match
  ).requester_lab.id;
};

export const makeBookingAndEquipmentData = (
  bookingsList: BookingsList,
  bookingDetails: Array<BookingDetails>,
  equipmentDetails: Array<EquipmentDetails>,
  isMaintenance: boolean
): Array<BookingAndEquipmentDetails> => {
  const relevant = getRelevantForMaintenanceType(isMaintenance, bookingDetails);
  return relevant.map((bookingDetail) => {
    const bookingAndEquipmentDetail: BookingAndEquipmentDetails = {
      bookingID: bookingDetail.id,
      start_time: getStartTime(bookingDetail.id, bookingsList),
      end_time: getEndTime(bookingDetail.id, bookingsList),
      requesterName: bookingDetail.requester.name,
      bookingType: bookingDetail.booking_type,
      maintenance_notes: bookingDetail.last_public_note?.message,
      duration: bookingDetail.duration,
      status: getStatus(bookingDetail.id, bookingsList),
      equipmentName: bookingDetail.equipment.name,
      manufacturer: getEquipment(bookingDetail.equipment.id, equipmentDetails)
        .manufacturer,
      model: getEquipment(bookingDetail.equipment.id, equipmentDetails).model,
      equipmentID: bookingDetail.equipment.id,
      labID: bookingDetail.requester_lab.id,
    };
    replaceNullWithEmptyString(bookingAndEquipmentDetail);
    return bookingAndEquipmentDetail;
  });
};

//equipment centric view, displays most recent booking  - if there is one
export const makeEquipmentWithBookingData = (
  bookingsList: BookingsList,
  bookingDetails: Array<BookingDetails>,
  equipmentDetails: Array<EquipmentDetails>,
  isMaintenance: boolean
): Array<EquipmentWithBookingDetails> => {
  const relevant = getRelevantForMaintenanceType(isMaintenance, bookingDetails);
  return equipmentDetails.map((equipmentDetail: EquipmentDetails) => {
    const booking = getMostRecentCompletedBooking(
      relevant,
      bookingsList,
      equipmentDetail.id
    );
    //if there is no matching booking, labID from another booking will be used to generate links to Clustermarket
    const labID = getALabID(bookingDetails);
    const equipmentAndBookingDetail: EquipmentWithBookingDetails = {
      equipmentID: String(equipmentDetail.id),
      equipmentName: equipmentDetail.name,
      manufacturer: equipmentDetail.manufacturer,
      model: equipmentDetail.model,
      maintenance_notes: booking ? booking.last_public_note?.message : "",
      bookingType: booking ? booking.booking_type : "",
      bookingID: booking ? booking.id : "",
      start_time: booking ? getStartTime(booking.id, bookingsList) : "",
      requesterName: booking ? booking.requester.name : "",
      labID: booking ? booking.requester_lab.id : labID,
    };
    replaceNullWithEmptyString(equipmentAndBookingDetail);
    return equipmentAndBookingDetail;
  });
};
