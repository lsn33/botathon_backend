package cl.teleton.planificacion.calculo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DistribucionDto(@NotBlank String codEsp, @NotBlank String tramo,
                              @NotNull BigDecimal pctDirecto, @NotNull BigDecimal pctIndirecto,
                              @NotNull BigDecimal pctNas, @Min(1) int minutosDirecta) {
}
