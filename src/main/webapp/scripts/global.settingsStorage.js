/* jshint maxerr: 100 */
/* global RS: true */

/**
 * Namespace for global variables and functions
 */
if (typeof RS === 'undefined') {
    var RS = {};
}

/* 
 * localStorage methods 
 */
RS.setDefaultUserSetting = function (key, value) {
  RS._setDefaultSetting(key, value, window.localStorage);
};

RS.saveUserSetting = function (key, value) {
  RS._saveSetting(key, value, window.localStorage);
};

RS.loadUserSetting = function (key) {
  return RS._loadSetting(key, window.localStorage);
};

RS.clearUserSetting = function (key) {
  RS._saveSetting(key, "__cleared", window.localStorage);
};

/* 
 * sessionStorage methods
 */
RS.setDefaultSessionSetting = function (key, value) {
  RS._setDefaultSetting(key, value, window.sessionStorage);
};

RS.saveSessionSetting = function (key, value) {
  RS._saveSetting(key, value, window.sessionStorage);
};

RS.loadSessionSetting = function (key) {
  return RS._loadSetting(key, window.sessionStorage);
};

RS.clearSessionSetting = function (key) {
  RS._saveSetting(key, "__cleared", window.sessionStorage);
};

/*
 * Sets the user setting in storage if it's available, and if the setting has no value yet
 */
RS._setDefaultSetting = function (key, value, storage) {
  if (!RS._loadSetting(key, storage)) {
    RS._saveSetting(key, value, storage);
  }
};

RS._saveSetting = function (key, value, storage) {
  if (RS._isStorageAvailable(storage)) {
    return storage.setItem(key, value);
  }
  console.warn('browser storage not available, can\'t set "' + key + '" to ' + value);
};

RS._loadSetting = function (key, storage) {
  if (RS._isStorageAvailable(storage)) {
    return storage.getItem(key);
  }
  console.warn('browser storage not available');
  return '__storage_unavailable';
};

RS._isStorageAvailable = function (storage) {
  try {
    var x = '__storage_test__';
    storage.setItem(x, x);
    storage.removeItem(x);
    return true;
  } catch (e) {
    return false;
  }
};
