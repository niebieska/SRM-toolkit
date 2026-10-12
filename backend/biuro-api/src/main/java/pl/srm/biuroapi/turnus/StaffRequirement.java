package pl.srm.biuroapi.turnus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record StaffRequirement(@NotBlank String categoryCode, @NotNull @Min(0)
        @JsonDeserialize(using = IntegerCountDeserializer.class) Integer requiredCount) {
    public static class IntegerCountDeserializer extends ValueDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.isExpectedNumberIntToken())
                return context.reportInputMismatch(Integer.class, "Staff requirement count must be an integer");
            return parser.getIntValue();
        }
    }
}
