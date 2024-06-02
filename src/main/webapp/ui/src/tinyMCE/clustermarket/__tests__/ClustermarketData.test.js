// @flow
/* eslint-env jest */
import {
  makeBookingAndEquipmentData,
  makeEquipmentWithBookingData,
  getMostRecentCompletedBooking,
  type BookingAndEquipmentDetails,
  type EquipmentWithBookingDetails,
} from "../ClustermarketData";
import BookingDetails from "./bookingsDetails.json";
import EquipmentDetails from "./equipmentDetails.json";
import BookingsList from "./allbookings.json";

const bookingResponses = [
  BookingDetails.COMPLETED_1,
  BookingDetails.CURRENT_2,
  BookingDetails.COMPLETED_3,
  BookingDetails.COMPLETED_4,
  BookingDetails.CURRENT_5,
];
const equipmentResponses = [
  EquipmentDetails["1"],
  EquipmentDetails["2"],
  EquipmentDetails["3"],
];
const row1: BookingAndEquipmentDetails = {
  bookingID: "COMPLETED_1",
  start_time: "2022-01-28 07:30:00",
  end_time: "2022-01-28 08:30:00",
  requesterName: "DBD",
  bookingType: "maintenance",
  maintenance_notes: "COMPLETED_1 maintenance Note",
  duration: 12,
  status: "Completed",
  equipmentName: "Mullis Lab Centrifuge",
  manufacturer: "Abbott",
  model: "Centrifuge Model",
  equipmentID: 1,
  labID: 11111,
};
const row2: BookingAndEquipmentDetails = {
  bookingID: "CURRENT_2",
  start_time: "2022-02-28 07:30:00",
  end_time: "2022-02-28 08:30:00",
  requesterName: "MN",
  bookingType: "use",
  maintenance_notes: "CURRENT_2 Note",
  duration: 8,
  status: "Booked",
  equipmentName: "Mullis Lab -70 Freezer",
  manufacturer: "",
  model: "Freezer Model",
  equipmentID: 2,
  labID: 11111,
};
const row3: BookingAndEquipmentDetails = {
  bookingID: "COMPLETED_3",
  start_time: "2022-02-28 06:30:00",
  end_time: "2022-02-28 08:30:00",
  requesterName: "ET",
  bookingType: "use",
  maintenance_notes: "COMPLETED_3 Note",
  duration: 1,
  status: "Completed",
  equipmentName: "Biochemistry Department Mass Spectrometer",
  manufacturer: "Oxford Analytics",
  model: "Mass Spec Model",
  equipmentID: 3,
  labID: 11111,
};
const row4: BookingAndEquipmentDetails = {
  bookingID: "COMPLETED_4",
  start_time: "2022-02-28 06:31:00",
  end_time: "2022-02-28 08:30:00",
  requesterName: "ET",
  bookingType: "use",
  maintenance_notes: "COMPLETED_4 Note",
  duration: 1,
  status: "Completed",
  equipmentName: "Biochemistry Department Mass Spectrometer",
  manufacturer: "Oxford Analytics",
  model: "Mass Spec Model",
  equipmentID: 3,
  labID: 11111,
};

const equipmentRow1: EquipmentWithBookingDetails = {
  equipmentID: "1",
  equipmentName: "Mullis Lab Centrifuge",
  manufacturer: "Abbott",
  model: "Centrifuge Model",
  maintenance_notes: "COMPLETED_1 maintenance Note",
  bookingType: "maintenance",
  bookingID: "COMPLETED_1",
  start_time: "2022-01-28 07:30:00",
  requesterName: "DBD",
  labID: 11111,
};

const equipmentRow2: EquipmentWithBookingDetails = {
  equipmentID: "2",
  equipmentName: "Mullis Lab -70 Freezer",
  manufacturer: "",
  model: "Freezer Model",
  maintenance_notes: "",
  bookingType: "",
  bookingID: "",
  start_time: "",
  requesterName: "",
  labID: 11111,
};

const equipmentRow3: EquipmentWithBookingDetails = {
  equipmentID: "3",
  equipmentName: "Biochemistry Department Mass Spectrometer",
  manufacturer: "Oxford Analytics",
  model: "Mass Spec Model",
  maintenance_notes: "COMPLETED_4 Note",
  bookingType: "use",
  bookingID: "COMPLETED_4",
  start_time: "2022-02-28 06:31:00",
  requesterName: "ET",
  labID: 11111,
};
const equipmentRow3_maintenance: EquipmentWithBookingDetails = {
  equipmentID: "3",
  equipmentName: "Biochemistry Department Mass Spectrometer",
  manufacturer: "Oxford Analytics",
  model: "Mass Spec Model",
  maintenance_notes: "",
  bookingType: "",
  bookingID: "",
  start_time: "",
  requesterName: "",
  labID: 11111,
};

describe("Bookings view: joins the data from an array of booking details responses and an array of equipment details responses into a booking details view", () => {
  it(" return an array of structured data", () => {
    const joinedData = makeBookingAndEquipmentData(
      BookingsList.data,
      bookingResponses,
      equipmentResponses,
      false
    );
    expect(joinedData[0]).toStrictEqual(row1);
    expect(joinedData[1]).toStrictEqual(row2);
    expect(joinedData[2]).toStrictEqual(row3);
    expect(joinedData[3]).toStrictEqual(row4);
  });
  it(" filters maintenance bookings", () => {
    const joinedData = makeBookingAndEquipmentData(
      BookingsList.data,
      bookingResponses,
      equipmentResponses,
      true
    );
    expect(joinedData[0]).toStrictEqual(row1);
    expect(joinedData.length).toBe(1);
  });
});

describe("Equipment view: joins the data from an array of booking details responses and an array of equipment details responses into an equipment details view", () => {
  it(" return an array of structured data", () => {
    const joinedData = makeEquipmentWithBookingData(
      BookingsList.data,
      bookingResponses,
      equipmentResponses,
      false
    );
    expect(joinedData[0]).toStrictEqual(equipmentRow1);
    expect(joinedData[1]).toStrictEqual(equipmentRow2);
    expect(joinedData[2]).toStrictEqual(equipmentRow3);
  });

  it(" filters maintenance bookings", () => {
    const joinedData = makeEquipmentWithBookingData(
      BookingsList.data,
      bookingResponses,
      equipmentResponses,
      true
    );
    expect(joinedData[0]).toStrictEqual(equipmentRow1);
    expect(joinedData[1]).toStrictEqual(equipmentRow2);
    expect(joinedData[2]).toStrictEqual(equipmentRow3_maintenance);
    expect(joinedData.length).toBe(3);
  });
});
describe("Get most recent booking for equipment", () => {
  it(" returns most recent booking when there is one", () => {
    const booking = getMostRecentCompletedBooking(
      bookingResponses,
      BookingsList.data,
      3
    );
    expect(booking).toStrictEqual(bookingResponses[3]);
  });
  it(" returns null when no booking matches", () => {
    const booking = getMostRecentCompletedBooking(
      bookingResponses,
      BookingsList.data,
      13
    );
    expect(booking).toBeNull();
  });
});
