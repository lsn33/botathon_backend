package cl.teleton.planificacion.ingesta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BukRequest(@NotBlank String nombreArchivo, String hashSha256, String bpProceso, String bpSesionId,
                         boolean reporteCompleto, @NotEmpty List<BukRegistro> registros) {
}
