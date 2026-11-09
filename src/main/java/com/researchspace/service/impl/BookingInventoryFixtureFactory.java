package com.researchspace.service.impl;

import static com.researchspace.model.inventory.Container.ContainerType.LIST;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import java.util.Locale;

class BookingInventoryFixtureFactory {

  private final ContainerDao containerDao;
  private final InstrumentDao instrumentDao;
  private final ContainerApiManager containerManager;
  private final InstrumentEntityApiManager instrumentManager;
  private final MessageSourceUtils messages;
  private static final String FIXTURE_DESCRIPTION_KEY = "bookingFixtures.description";
  private static final Locale FIXTURE_LOCALE = Locale.forLanguageTag("en-US");

  BookingInventoryFixtureFactory(
      ContainerDao containerDao,
      InstrumentDao instrumentDao,
      ContainerApiManager containerManager,
      InstrumentEntityApiManager instrumentManager,
      MessageSourceUtils messages) {
    this.containerDao = containerDao;
    this.instrumentDao = instrumentDao;
    this.containerManager = containerManager;
    this.instrumentManager = instrumentManager;
    this.messages = messages;
  }

  Instrument ensureInstrument(String name, User owner) {
    String fixtureDescription = message(FIXTURE_DESCRIPTION_KEY);
    return instrumentDao.findInstrumentsByName(name, owner).stream()
        .filter(instrument -> !instrument.isDeleted())
        .filter(instrument -> fixtureDescription.equals(instrument.getDescription()))
        .findFirst()
        .orElseGet(
            () -> {
              ApiInstrument request = new ApiInstrument();
              request.setName(name);
              request.setDescription(fixtureDescription);
              ApiInstrument created = instrumentManager.createNewApiInstrument(request, owner);
              return instrumentDao.get(created.getId());
            });
  }

  Container ensureContainer(String name, User owner, boolean includeDeleted) {
    String fixtureDescription = message(FIXTURE_DESCRIPTION_KEY);
    return containerDao.getAll().stream()
        .filter(container -> name.equals(container.getName()))
        .filter(container -> owner.getId().equals(container.getOwner().getId()))
        .filter(container -> fixtureDescription.equals(container.getDescription()))
        .filter(container -> includeDeleted || !container.isDeleted())
        .findFirst()
        .orElseGet(
            () -> {
              ApiContainer request = new ApiContainer(name, LIST);
              request.setDescription(fixtureDescription);
              ApiContainer created = containerManager.createNewApiContainer(request, owner);
              return containerDao.get(created.getId());
            });
  }

  String message(String key) {
    return messages.getMessageForLocale(key, FIXTURE_LOCALE);
  }

  void moveToParent(Instrument instrument, Container parent) {
    if (!hasParent(instrument, parent)) {
      instrument.moveToNewParent(parent);
      instrumentDao.save(instrument);
    }
  }

  boolean hasParent(Instrument instrument, Container parent) {
    return instrument.getParentContainer() != null
        && parent.getId().equals(instrument.getParentContainer().getId());
  }

  void removeFromParent(Instrument instrument) {
    if (instrument.getParentContainer() != null) {
      instrument.removeFromCurrentParent();
      instrumentDao.save(instrument);
    }
  }
}
