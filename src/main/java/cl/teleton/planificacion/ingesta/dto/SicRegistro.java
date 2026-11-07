package cl.teleton.planificacion.ingesta.dto;

import java.time.LocalDate;

public record SicRegistro(Long personaId, Integer institutoId, LocalDate periodo, Integer atencionesRealizadas) {
}
