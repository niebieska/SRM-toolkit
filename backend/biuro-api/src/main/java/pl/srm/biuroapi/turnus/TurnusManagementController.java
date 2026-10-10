package pl.srm.biuroapi.turnus;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/turnuses")
public class TurnusManagementController {
    private final TurnusCatalogue catalogue;
    public TurnusManagementController(TurnusCatalogue catalogue) { this.catalogue = catalogue; }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public org.springframework.http.ResponseEntity<pl.srm.biuroapi.common.api.ApiError> malformedRequest() {
        return org.springframework.http.ResponseEntity.badRequest()
                .body(new pl.srm.biuroapi.common.api.ApiError(400, "Nieprawidłowe dane turnusu."));
    }
    @GetMapping public List<TurnusDefinition> list() { return catalogue.list(); }
    @GetMapping("/{code}") public TurnusDefinition get(@PathVariable String code) { return catalogue.get(code); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public TurnusDefinition create(@Valid @RequestBody TurnusRequest request) { return catalogue.create(request); }
    @PatchMapping("/{code}")
    public TurnusDefinition update(@PathVariable String code, @Valid @RequestBody TurnusRequest request) {
        return catalogue.update(code, request);
    }
}
