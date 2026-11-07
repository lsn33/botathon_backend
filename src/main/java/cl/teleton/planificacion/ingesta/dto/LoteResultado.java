package cl.teleton.planificacion.ingesta.dto;

public record LoteResultado(long loteId, String estado, int total, int nuevos, int modificados,
                            int sinCambios, int bajas, int errores) {
}
