package cl.teleton.planificacion.calculo.controller;

import cl.teleton.planificacion.auditoria.service.AuditoriaService;
import cl.teleton.planificacion.auth.service.UsuarioContext;
import cl.teleton.planificacion.calculo.dto.DistribucionDto;
import cl.teleton.planificacion.calculo.dto.ParametroDto;
import cl.teleton.planificacion.calculo.service.CalculoService;
import cl.teleton.planificacion.calculo.service.ParametrosService;
import cl.teleton.planificacion.calculo.service.PlanificacionService;
import cl.teleton.planificacion.common.NegocioException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PlanificacionController {

    private final PlanificacionService planificacion;
    private final CalculoService calculo;
    private final ParametrosService parametros;
    private final AuditoriaService auditoria;
    private final ObjectMapper mapper;

    public PlanificacionController(PlanificacionService planificacion, CalculoService calculo,
                                   ParametrosService parametros, AuditoriaService auditoria, ObjectMapper mapper) {
        this.planificacion = planificacion;
        this.calculo = calculo;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.mapper = mapper;
    }

    @GetMapping("/dashboard/resumen")
    public Map<String, Object> resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodo,
            @RequestParam(required = false) Integer institutoId) {
        return planificacion.resumen(periodo, UsuarioContext.institutoPermitido(institutoId));
    }

    @GetMapping("/dashboard/plan-vs-real")
    public List<Map<String, Object>> planVsReal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodo,
            @RequestParam(required = false) Integer institutoId) {
        return planificacion.planVsReal(periodo, UsuarioContext.institutoPermitido(institutoId));
    }

    @GetMapping("/dashboard/periodos")
    public List<LocalDate> periodos() {
        return planificacion.periodos();
    }

    @GetMapping("/planificacion/profesional/{personaId}")
    public List<Map<String, Object>> porProfesional(
            @PathVariable long personaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodo,
            @RequestParam(required = false) Integer institutoId) {
        return planificacion.porProfesional(personaId, periodo, UsuarioContext.institutoPermitido(institutoId));
    }

    @PostMapping("/planificacion/recalcular")
    public Map<String, Object> recalcular(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodo) {
        int n = calculo.recalcular(periodo == null ? null : List.of(periodo));
        auditoria.registrar(UsuarioContext.email(), "RECALCULO", "planificacion.resultado",
                "periodo=" + periodo + " filas=" + n);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("filasRecalculadas", n);
        return r;
    }

    @GetMapping("/parametros/{tipo}")
    public Object obtenerParametros(@PathVariable String tipo) {
        return parametros.obtener(tipo);
    }

    /** tipo = general (lista de ParametroDto) | distribucion (lista de DistribucionDto). */
    @PutMapping("/parametros/{tipo}")
    public Map<String, Object> actualizarParametros(@PathVariable String tipo, @RequestBody List<Map<String, Object>> body) {
        switch (tipo.toLowerCase()) {
            case "general" -> parametros.actualizarGeneral(
                    mapper.convertValue(body, new TypeReference<List<ParametroDto>>() { }));
            case "distribucion" -> parametros.actualizarDistribucion(
                    mapper.convertValue(body, new TypeReference<List<DistribucionDto>>() { }));
            default -> throw new NegocioException(HttpStatus.NOT_FOUND, "Tipo de parámetro desconocido: " + tipo);
        }
        return Map.of("ok", true, "mensaje", "Parámetros actualizados y planificación recalculada");
    }
}
