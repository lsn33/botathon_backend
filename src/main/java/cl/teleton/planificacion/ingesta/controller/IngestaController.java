package cl.teleton.planificacion.ingesta.controller;

import cl.teleton.planificacion.auth.service.UsuarioContext;
import cl.teleton.planificacion.common.NegocioException;
import cl.teleton.planificacion.ingesta.dto.BukRequest;
import cl.teleton.planificacion.ingesta.dto.LoteResultado;
import cl.teleton.planificacion.ingesta.dto.SicRequest;
import cl.teleton.planificacion.ingesta.repository.LoteRepository;
import cl.teleton.planificacion.ingesta.service.IngestaService;
import cl.teleton.planificacion.ingesta.service.StagingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class IngestaController {

    private final IngestaService ingesta;
    private final StagingService staging;
    private final LoteRepository lotes;

    public IngestaController(IngestaService ingesta, StagingService staging, LoteRepository lotes) {
        this.ingesta = ingesta;
        this.staging = staging;
        this.lotes = lotes;
    }

    /** Blue Prism -> backend (header X-API-Key). */
    @PostMapping("/ingesta/buk")
    public LoteResultado buk(@Valid @RequestBody BukRequest req) {
        return ingesta.procesarBuk(req, UsuarioContext.email());
    }

    @PostMapping("/ingesta/sic")
    public LoteResultado sic(@Valid @RequestBody SicRequest req) {
        return ingesta.procesarSic(req, UsuarioContext.email());
    }

    /** Procesa lo que Blue Prism dejo en las tablas staging de SQL Server. tipo = BUK | SIC */
    @PostMapping("/ingesta/procesar-staging")
    public LoteResultado procesarStaging(@RequestParam String tipo,
                                         @RequestParam(defaultValue = "false") boolean reporteCompleto) {
        String usuario = UsuarioContext.email();
        return switch (tipo.toUpperCase()) {
            case "BUK" -> staging.procesarBuk(reporteCompleto, usuario);
            case "SIC" -> staging.procesarSic(usuario);
            default -> throw new NegocioException(HttpStatus.BAD_REQUEST, "tipo debe ser BUK o SIC");
        };
    }

    @GetMapping("/lotes")
    public List<Map<String, Object>> listarLotes(@RequestParam(defaultValue = "100") int limite) {
        return lotes.listar(limite);
    }

    @GetMapping("/lotes/{id}/errores")
    public List<Map<String, Object>> errores(@PathVariable long id) {
        return lotes.errores(id);
    }
}
