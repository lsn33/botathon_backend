package cl.teleton.planificacion.ingesta.service;

import cl.teleton.planificacion.auditoria.service.AuditoriaService;
import cl.teleton.planificacion.calculo.service.CalculoService;
import cl.teleton.planificacion.catalogo.service.CatalogoService;
import cl.teleton.planificacion.common.NegocioException;
import cl.teleton.planificacion.ingesta.dto.BukRegistro;
import cl.teleton.planificacion.ingesta.dto.BukRequest;
import cl.teleton.planificacion.ingesta.dto.LoteResultado;
import cl.teleton.planificacion.ingesta.dto.SicRegistro;
import cl.teleton.planificacion.ingesta.dto.SicRequest;
import cl.teleton.planificacion.ingesta.repository.LoteRepository;
import cl.teleton.planificacion.rrhh.dto.CambioDotacion;
import cl.teleton.planificacion.rrhh.dto.DotacionCmd;
import cl.teleton.planificacion.rrhh.service.DotacionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Service
public class IngestaService {

    private static final BigDecimal MAX_HORAS = BigDecimal.valueOf(60);

    private final LoteRepository lotes;
    private final DotacionService dotacion;
    private final CatalogoService catalogo;
    private final CalculoService calculo;
    private final AuditoriaService auditoria;
    private final JdbcTemplate jdbc;
    private final boolean crearCargosFaltantes;

    public IngestaService(LoteRepository lotes, DotacionService dotacion, CatalogoService catalogo,
                          CalculoService calculo, AuditoriaService auditoria, JdbcTemplate jdbc,
                          @Value("${app.ingesta.crear-cargos-faltantes:true}") boolean crearCargosFaltantes) {
        this.lotes = lotes;
        this.dotacion = dotacion;
        this.catalogo = catalogo;
        this.calculo = calculo;
        this.auditoria = auditoria;
        this.jdbc = jdbc;
        this.crearCargosFaltantes = crearCargosFaltantes;
    }

    // ---------------------------------------------------------------- BUK

    @Transactional
    public LoteResultado procesarBuk(BukRequest req, String usuario) {
        String hash = req.hashSha256();
        if (hash != null && !hash.isBlank() && lotes.existeHashProcesado(hash, "BUK")) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "El archivo ya fue procesado (mismo hash SHA-256): " + req.nombreArchivo());
        }
        long loteId = lotes.crear("BUK", req.nombreArchivo(), hash, req.bpProceso(), req.bpSesionId(), req.reporteCompleto());

        int nuevos = 0, modificados = 0, sinCambios = 0, errores = 0, fila = 0;
        Set<String> vistos = new HashSet<>();
        Set<LocalDate> periodos = new HashSet<>();

        for (BukRegistro r : req.registros()) {
            fila++;
            String error = validarBuk(r);
            if (error != null) {
                lotes.agregarError(loteId, fila, error);
                errores++;
                continue;
            }
            LocalDate periodo = r.periodo().withDayOfMonth(1);
            String cargo = r.cargoBuk().trim().toUpperCase();
            String codEsp = r.codEsp() == null || r.codEsp().isBlank() ? null : r.codEsp().trim().toUpperCase();

            if (!catalogo.existeCargo(cargo)) {
                if (crearCargosFaltantes && codEsp != null) {
                    catalogo.crearCargo(cargo, codEsp);
                } else {
                    lotes.agregarError(loteId, fila, "Cargo no existe en catálogo: " + cargo);
                    errores++;
                    continue;
                }
            }

            var cmd = new DotacionCmd(r.personaId(), r.institutoId(), periodo, cargo, codEsp,
                    r.horasSemanales(), r.tipoContrato(), r.tipoTrabajador());
            CambioDotacion cambio = dotacion.upsert(loteId, cmd);
            switch (cambio) {
                case NUEVO -> nuevos++;
                case MODIFICADO -> modificados++;
                case SIN_CAMBIOS -> sinCambios++;
            }
            vistos.add(DotacionService.clave(r.personaId(), r.institutoId(), cargo, periodo));
            periodos.add(periodo);
        }

        int bajas = 0;
        if (req.reporteCompleto() && !periodos.isEmpty()) {
            bajas = dotacion.darDeBajaAusentes(loteId, periodos, vistos);
        }

        int recalculadas = 0;
        if (nuevos + modificados + bajas > 0) {
            recalculadas = calculo.recalcular(periodos);
        }

        String estado = errores == 0 ? "OK" : "CON_ERRORES";
        lotes.cerrar(loteId, estado, req.registros().size(), nuevos, modificados, sinCambios, bajas, errores);
        auditoria.registrar(usuario, "INGESTA_BUK", "ingesta.lote",
                "lote=" + loteId + " archivo=" + req.nombreArchivo() + " nuevos=" + nuevos + " mod=" + modificados
                        + " bajas=" + bajas + " errores=" + errores + " recalculadas=" + recalculadas);
        return new LoteResultado(loteId, estado, req.registros().size(), nuevos, modificados, sinCambios, bajas, errores);
    }

    private String validarBuk(BukRegistro r) {
        if (r == null) return "Registro vacío";
        if (r.personaId() == null) return "personaId es obligatorio";
        if (r.institutoId() == null) return "institutoId es obligatorio";
        if (r.periodo() == null) return "periodo es obligatorio (formato yyyy-MM-dd)";
        if (r.cargoBuk() == null || r.cargoBuk().isBlank()) return "cargoBuk es obligatorio";
        if (r.horasSemanales() == null || r.horasSemanales().signum() <= 0 || r.horasSemanales().compareTo(MAX_HORAS) > 0) {
            return "horasSemanales debe estar entre 0 y 60";
        }
        if (!catalogo.existeInstituto(r.institutoId())) return "Instituto no existe en catálogo: " + r.institutoId();
        return null;
    }

    // ---------------------------------------------------------------- SIC

    @Transactional
    public LoteResultado procesarSic(SicRequest req, String usuario) {
        String hash = req.hashSha256();
        if (hash != null && !hash.isBlank() && lotes.existeHashProcesado(hash, "SIC")) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "El archivo ya fue procesado (mismo hash SHA-256): " + req.nombreArchivo());
        }
        long loteId = lotes.crear("SIC", req.nombreArchivo(), hash, req.bpProceso(), req.bpSesionId(), false);
        int nuevos = 0, modificados = 0, sinCambios = 0, errores = 0, fila = 0;

        for (SicRegistro r : req.registros()) {
            fila++;
            String error = validarSic(r);
            if (error != null) {
                lotes.agregarError(loteId, fila, error);
                errores++;
                continue;
            }
            LocalDate periodo = r.periodo().withDayOfMonth(1);
            Integer actual = jdbc.query("SELECT atenciones_realizadas FROM sic.atencion " +
                            "WHERE persona_id = ? AND instituto_id = ? AND periodo = ?",
                    (rs, i) -> rs.getInt(1), r.personaId(), r.institutoId(), periodo).stream().findFirst().orElse(null);
            if (actual == null) {
                jdbc.update("INSERT INTO sic.atencion (persona_id, instituto_id, periodo, atenciones_realizadas, lote_id) " +
                        "VALUES (?,?,?,?,?)", r.personaId(), r.institutoId(), periodo, r.atencionesRealizadas(), loteId);
                nuevos++;
            } else if (!actual.equals(r.atencionesRealizadas())) {
                jdbc.update("UPDATE sic.atencion SET atenciones_realizadas = ?, lote_id = ?, actualizado_en = SYSUTCDATETIME() " +
                                "WHERE persona_id = ? AND instituto_id = ? AND periodo = ?",
                        r.atencionesRealizadas(), loteId, r.personaId(), r.institutoId(), periodo);
                modificados++;
            } else {
                sinCambios++;
            }
        }

        String estado = errores == 0 ? "OK" : "CON_ERRORES";
        lotes.cerrar(loteId, estado, req.registros().size(), nuevos, modificados, sinCambios, 0, errores);
        auditoria.registrar(usuario, "INGESTA_SIC", "ingesta.lote",
                "lote=" + loteId + " archivo=" + req.nombreArchivo() + " nuevos=" + nuevos + " mod=" + modificados
                        + " errores=" + errores);
        return new LoteResultado(loteId, estado, req.registros().size(), nuevos, modificados, sinCambios, 0, errores);
    }

    private String validarSic(SicRegistro r) {
        if (r == null) return "Registro vacío";
        if (r.personaId() == null) return "personaId es obligatorio";
        if (r.institutoId() == null) return "institutoId es obligatorio";
        if (r.periodo() == null) return "periodo es obligatorio (formato yyyy-MM-dd)";
        if (r.atencionesRealizadas() == null || r.atencionesRealizadas() < 0) return "atencionesRealizadas debe ser >= 0";
        if (!catalogo.existeInstituto(r.institutoId())) return "Instituto no existe en catálogo: " + r.institutoId();
        return null;
    }
}
