package cl.teleton.planificacion.calculo.service;

import cl.teleton.planificacion.auditoria.service.AuditoriaService;
import cl.teleton.planificacion.auth.service.UsuarioContext;
import cl.teleton.planificacion.calculo.dto.DistribucionDto;
import cl.teleton.planificacion.calculo.dto.ParametroDto;
import cl.teleton.planificacion.calculo.repository.ParametrosRepository;
import cl.teleton.planificacion.common.NegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ParametrosService {

    private final ParametrosRepository repo;
    private final CalculoService calculo;
    private final AuditoriaService auditoria;

    public ParametrosService(ParametrosRepository repo, CalculoService calculo, AuditoriaService auditoria) {
        this.repo = repo;
        this.calculo = calculo;
        this.auditoria = auditoria;
    }

    public Object obtener(String tipo) {
        return switch (tipo.toLowerCase()) {
            case "general" -> repo.general();
            case "distribucion" -> repo.distribuciones();
            default -> throw new NegocioException(HttpStatus.NOT_FOUND, "Tipo de parámetro desconocido: " + tipo);
        };
    }

    @Transactional
    public void actualizarGeneral(List<ParametroDto> items) {
        for (ParametroDto p : items) {
            if (!repo.actualizarGeneral(p.clave(), p.valor())) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "Parámetro inexistente: " + p.clave());
            }
        }
        auditoria.registrar(UsuarioContext.email(), "PARAMETROS_GENERAL", "parametros.parametro", items.toString());
        calculo.recalcular(null);
    }

    @Transactional
    public void actualizarDistribucion(List<DistribucionDto> items) {
        for (DistribucionDto d : items) {
            if (d.pctDirecto().add(d.pctIndirecto()).add(d.pctNas()).compareTo(BigDecimal.valueOf(100)) != 0) {
                throw new NegocioException(HttpStatus.BAD_REQUEST,
                        "Los porcentajes deben sumar 100 (" + d.codEsp() + "/" + d.tramo() + ")");
            }
            repo.upsertDistribucion(d);
        }
        auditoria.registrar(UsuarioContext.email(), "PARAMETROS_DISTRIBUCION", "parametros.distribucion", items.toString());
        calculo.recalcular(null);
    }
}
