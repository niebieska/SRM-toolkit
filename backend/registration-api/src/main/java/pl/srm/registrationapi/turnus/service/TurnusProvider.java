package pl.srm.registrationapi.turnus.service;

import pl.srm.registrationapi.turnus.domain.Turnus;

import java.util.List;

public interface TurnusProvider {

    List<Turnus> getAvailableTurnuses();

    Turnus getByCode(String code);

}
