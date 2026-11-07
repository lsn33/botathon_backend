package cl.teleton.planificacion.rrhh.controller;

import cl.teleton.planificacion.auth.service.UsuarioContext;
import cl.teleton.planificacion.calculo.service.PlanificacionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dotacion")
public class ProfesionalController {

    private final PlanificacionService service;

    public ProfesionalController(PlanificacionService service) {
        this.service = service;
    }

    @GetMapping("/profesionales")
    public Map<String, Object> profesionales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodo,
            @RequestParam(required = false) Integer institutoId,
            @RequestParam(required = false) Long personaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listarProfesionales(periodo, UsuarioContext.institutoPermitido(institutoId), personaId, page, size);
    }
}
