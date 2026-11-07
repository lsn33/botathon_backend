package cl.teleton.planificacion.rrhh.service;

import cl.teleton.planificacion.rrhh.dto.CambioDotacion;
import cl.teleton.planificacion.rrhh.dto.DotacionCmd;
import cl.teleton.planificacion.rrhh.entity.Dotacion;
import cl.teleton.planificacion.rrhh.repository.DotacionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

@Service
public class DotacionService {

    private final DotacionRepository repo;

    public DotacionService(DotacionRepository repo) {
        this.repo = repo;
    }

    public static String clave(long personaId, int institutoId, String cargoBuk, LocalDate periodo) {
        return personaId + "|" + institutoId + "|" + cargoBuk.toUpperCase() + "|" + periodo;
    }

    /** Inserta o actualiza una fila de dotacion y deja historial del cambio. */
    public CambioDotacion upsert(long loteId, DotacionCmd c) {
        repo.asegurarPersona(c.personaId());
        Optional<Dotacion> existente = repo.buscar(c.personaId(), c.institutoId(), c.cargoBuk(), c.periodo());

        if (existente.isEmpty()) {
            long id = repo.insertar(c);
            repo.historial(id, loteId, "ALTA", "horas=" + c.horasSemanales());
            return CambioDotacion.NUEVO;
        }

        Dotacion d = existente.get();
        boolean cambio = d.horasSemanales().compareTo(c.horasSemanales()) != 0
                || !igual(d.codEsp(), c.codEsp())
                || !igual(d.tipoContrato(), c.tipoContrato())
                || !igual(d.tipoTrabajador(), c.tipoTrabajador());

        if (!d.activo()) {
            repo.actualizar(d.id(), c);
            repo.historial(d.id(), loteId, "REACTIVACION", "horas=" + c.horasSemanales());
            return CambioDotacion.MODIFICADO;
        }
        if (cambio) {
            repo.actualizar(d.id(), c);
            repo.historial(d.id(), loteId, "MODIFICACION",
                    "horas " + d.horasSemanales() + " -> " + c.horasSemanales()
                            + "; contrato " + d.tipoContrato() + " -> " + c.tipoContrato()
                            + "; trabajador " + d.tipoTrabajador() + " -> " + c.tipoTrabajador());
            return CambioDotacion.MODIFICADO;
        }
        return CambioDotacion.SIN_CAMBIOS;
    }

    /** Si el reporte es completo, lo activo que no vino en el archivo pasa a baja. */
    public int darDeBajaAusentes(long loteId, Set<LocalDate> periodos, Set<String> vistos) {
        int bajas = 0;
        for (var a : repo.activas(periodos)) {
            if (!vistos.contains(clave(a.personaId(), a.institutoId(), a.cargoBuk(), a.periodo()))) {
                repo.desactivar(a.id());
                repo.historial(a.id(), loteId, "BAJA", "No vino en reporte completo");
                bajas++;
            }
        }
        return bajas;
    }

    private static boolean igual(String a, String b) {
        return (a == null ? "" : a.trim()).equalsIgnoreCase(b == null ? "" : b.trim());
    }
}
