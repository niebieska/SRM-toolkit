package pl.srm.registrationapi.turnus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pl.srm.registrationapi.turnus.domain.SeasonType;
import pl.srm.registrationapi.turnus.domain.Turnus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "srm")
public class TurnusProperties {

    private List<TurnusEntry> turnuses = new ArrayList<>();

    public List<TurnusEntry> getTurnuses() {
        return turnuses;
    }

    public void setTurnuses(List<TurnusEntry> turnuses) {
        this.turnuses = turnuses;
    }

    public static class TurnusEntry {

        private String turnusCode;
        private String turnusName;
        private String turnusDescription;
        private String turnusLocation;
        private SeasonType seasonType;
        private Integer seasonYear;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer minAge;
        private Integer maxAge;
        private Integer capacity;
        private boolean active;
        private boolean registrationOpen;

        public Turnus toDomain() {
            return new Turnus(
                    turnusCode,
                    turnusName,
                    turnusDescription,
                    turnusLocation,
                    seasonType,
                    seasonYear,
                    startDate,
                    endDate,
                    minAge,
                    maxAge,
                    capacity,
                    active,
                    registrationOpen
            );
        }

        public String getTurnusCode() { return turnusCode; }
        public void setTurnusCode(String turnusCode) { this.turnusCode = turnusCode; }

        public String getTurnusName() { return turnusName; }
        public void setTurnusName(String turnusName) { this.turnusName = turnusName; }

        public String getTurnusDescription() { return turnusDescription; }
        public void setTurnusDescription(String turnusDescription) { this.turnusDescription = turnusDescription; }

        public String getTurnusLocation() { return turnusLocation; }
        public void setTurnusLocation(String turnusLocation) { this.turnusLocation = turnusLocation; }

        public SeasonType getSeasonType() { return seasonType; }
        public void setSeasonType(SeasonType seasonType) { this.seasonType = seasonType; }

        public Integer getSeasonYear() { return seasonYear; }
        public void setSeasonYear(Integer seasonYear) { this.seasonYear = seasonYear; }

        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

        public Integer getMinAge() { return minAge; }
        public void setMinAge(Integer minAge) { this.minAge = minAge; }

        public Integer getMaxAge() { return maxAge; }
        public void setMaxAge(Integer maxAge) { this.maxAge = maxAge; }

        public Integer getCapacity() { return capacity; }
        public void setCapacity(Integer capacity) { this.capacity = capacity; }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public boolean isRegistrationOpen() { return registrationOpen; }
        public void setRegistrationOpen(boolean registrationOpen) { this.registrationOpen = registrationOpen; }
    }
}
