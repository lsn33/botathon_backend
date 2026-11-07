package cl.teleton.planificacion.catalogo.controller;

import cl.teleton.planificacion.catalogo.service.CatalogoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/catalogos")
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @GetMapping("/institutos")
    public List<Map<String, Object>> institutos() {
        return service.institutos();
    }

    @GetMapping("/cargos")
    public List<Map<String, Object>> cargos() {
        return service.cargos();
    }
}
