package pl.srm.registrationapi.turnus.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.srm.registrationapi.turnus.domain.Turnus;

import java.util.List;

public class TurnusListWrapper {

    @JsonProperty("turnuses")
    private List<Turnus> turnusList;

    public List<Turnus> getTurnusList() {
        return turnusList;
    }

    public void setTurnusList(List<Turnus> turnusList) {
        this.turnusList = turnusList;
    }
}