package pl.srm.biuroapi.turnus;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pl.srm.biuroapi.common.api.ApiError;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import pl.srm.biuroapi.season.StaffingCategories;

@RestController
@RequestMapping("/api/turnuses")
public class TurnusManagementController {
    private final TurnusCatalogue catalogue;
    private final StaffingCategories categories;
    public TurnusManagementController(TurnusCatalogue catalogue, StaffingCategories categories) {
        this.catalogue = catalogue;
        this.categories = categories;
    }
    @GetMapping("/staffing-categories")
    public List<StaffingCategories.Category> staffingCategories(@RequestParam TurnusDefinition.SeasonType seasonType) {
        return categories.get(seasonType);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> malformedRequest() {
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "Nieprawidłowe dane turnusu."));
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
