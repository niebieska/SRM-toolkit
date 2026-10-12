package pl.srm.registrationapi.turnus.service;

import pl.srm.registrationapi.registration.model.RegistrationType;

import pl.srm.registrationapi.turnus.model.Turnus;

import java.util.List;

public interface TurnusProvider {

    List<Turnus> getAvailableTurnuses();

    List<Turnus> getAvailableTurnuses(RegistrationType type);

    Turnus getByCode(String code);

}
