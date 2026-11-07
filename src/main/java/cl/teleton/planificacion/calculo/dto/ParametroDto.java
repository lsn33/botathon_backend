package cl.teleton.planificacion.calculo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ParametroDto(@NotBlank String clave, @NotNull BigDecimal valor, String descripcion) {
}
