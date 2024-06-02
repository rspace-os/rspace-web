'use strict';

const react_device_detect = jest.createMockFromModule('react-device-detect');

export let isMobile = false;
export const __setIsMobile = (value) => {
  isMobile = value;
};
