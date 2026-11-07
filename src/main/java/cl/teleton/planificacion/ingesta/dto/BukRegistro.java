package cl.teleton.planificacion.ingesta.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BukRegistro(Long personaId, Integer institutoId, LocalDate periodo, String cargoBuk,
                          String codEsp, BigDecimal horasSemanales, String tipoContrato, String tipoTrabajador) {
}
