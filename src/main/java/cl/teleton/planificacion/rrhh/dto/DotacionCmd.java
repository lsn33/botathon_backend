package cl.teleton.planificacion.rrhh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DotacionCmd(long personaId, int institutoId, LocalDate periodo, String cargoBuk,
                          String codEsp, BigDecimal horasSemanales, String tipoContrato, String tipoTrabajador) {
}
