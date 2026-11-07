package cl.teleton.planificacion.rrhh.entity;

import java.math.BigDecimal;

public record Dotacion(long id, BigDecimal horasSemanales, String codEsp,
                       String tipoContrato, String tipoTrabajador, boolean activo) {
}
