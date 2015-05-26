/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package algoritmo;

import controladores.AsignacionHorarioControlador;
import controladores.AsignacionPermisoControlador;

import controladores.DetalleGrupoControlador;
import controladores.FeriadoControlador;
import controladores.MarcacionControlador;
import controladores.RegistroAsistenciaControlador;
import controladores.TCAnalisisControlador;
import controladores.TCSistemaControlador;
import controladores.VacacionControlador;
import entidades.AsignacionHorario;
import entidades.AsignacionPermiso;
import entidades.DetalleGrupoHorario;
import entidades.DetalleRegistroAsistencia;
import entidades.Feriado;
import entidades.GrupoHorario;
import entidades.Horario;
import entidades.Jornada;
import entidades.Marcacion;
import entidades.Permiso;
import entidades.RegistroAsistencia;
import entidades.TCAnalisis;
import entidades.TCSistema;
import entidades.Vacacion;
import com.personal.utiles.FechaUtil;
import controladores.Controlador;
import controladores.PermisoControlador;
import controladores.TipoPermisoControlador;
import entidades.Turno;
import entidades.escalafon.Empleado;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.apache.log4j.Logger;

/**
 *
 * @author RyuujiMD
 */
public class AnalisisAsistencia {

    private TCAnalisisControlador tcac;
    private FeriadoControlador fc;
    private AsignacionPermisoControlador apc;
    private VacacionControlador vc;
    private MarcacionControlador mc;
    private TCSistemaControlador tcsc;
    private DetalleGrupoControlador dgc;
    private AsignacionHorarioControlador ahc;
    private RegistroAsistenciaControlador rac;
    private PermisoControlador pc;
    private TipoPermisoControlador tpc;
    private static final Logger LOG = Logger.getLogger(AnalisisAsistencia.class.getName());

    private final int MIN_FIN_MARCACION = 500;
    private final int MIN_ANTES_INICIO_PERMISO = 30;

    public AnalisisAsistencia() {
        iniciar();
    }

    private void iniciar() {
        tcac = new TCAnalisisControlador();
        fc = new FeriadoControlador();
        apc = new AsignacionPermisoControlador();
        vc = new VacacionControlador();
        mc = new MarcacionControlador();
        dgc = new DetalleGrupoControlador();
        ahc = new AsignacionHorarioControlador();
        rac = new RegistroAsistenciaControlador();
        tcsc = TCSistemaControlador.getInstance();
        pc = new PermisoControlador();
        tpc = new TipoPermisoControlador();
    }

    private TCAnalisis obtenerPuntoPartida(Empleado empleado) {
        tcac.getDao().getEntityManager().clear();
        TCAnalisis partida = tcac.buscarPorId(empleado.getNroDocumento());
        if (partida == null) {
            partida = new TCAnalisis();
            TCSistema sistema = tcsc.buscarPorId("BIOSIS");

            Date fechaCero = sistema.getFechaCero();
            Date contrato = empleado.getFichaLaboral().getFechaInicio() == null ? fechaCero : empleado.getFichaLaboral().getFechaInicio();

            if (contrato.compareTo(fechaCero) <= 0) {
                partida.setFecha(fechaCero);
                partida.setHora(sistema.getHoraCero());
            } else {
                partida.setFecha(contrato);
                partida.setHora(contrato);
            }
        }
        return partida;
    }

    private TCAnalisis obtenerPuntoLlegada() {
        TCAnalisis llegada = new TCAnalisis();
        Date fechaHoraActual = new Date();
        llegada.setFecha(FechaUtil.soloFecha(fechaHoraActual));
        llegada.setHora(FechaUtil.soloHora(fechaHoraActual));

        return llegada;
    }

    public List<RegistroAsistencia> analizarEmpleados(List<Empleado> empleados) {
        //LA FECHA Y HORA DE FINAL DEL ANALISIS ES LA MISMA DE LA CONSULTA
        List<RegistroAsistencia> registros = new ArrayList<>();
        TCAnalisis llegada = obtenerPuntoLlegada();
        for (Empleado empleado : empleados) {
            //OBTENEMOS LA FECHA Y HORA DE PARTIDA DEL ANALISIS
            TCAnalisis partida = obtenerPuntoPartida(empleado);
            System.out.println(String.format("PUNTOs DE PARTIDA - LLEGADA: %s %s - %s %s", partida.getFecha(), partida.getHora(), llegada.getFecha(), llegada.getHora()));
            //OBTENEMOS LOS HORARIOS DEL EMPLEADO
            List<Horario> horarios = obtenerHorarios(empleado);

            //ANALIZAMOS POR HORARIO
            System.out.println("NRO DE HORARIOS: " + horarios.size());
            for (Horario horario : horarios) {
                System.out.println("POR ANALIZAR HORARIO: " + horario.getNombre());
                if (horario.getTipo() == 'A') {
                    registros.addAll(analizarHorario(empleado, horario, partida, llegada));
                }

            }
            if (!registros.isEmpty()) {
                this.rac.guardarLote(registros);
                llegada.setEmpleado(empleado.getNroDocumento());
                this.tcac.modificar(llegada);
            }

            registros.clear();
        }
        return registros;
    }

    public List<Horario> obtenerHorarios(Empleado empleado) {
        //PRIMERO OBTENEMOS LOS GRUPOS HORARIOS ASIGNADOS

        List<DetalleGrupoHorario> detalles = dgc.buscarXEmpleado(empleado);
        List<GrupoHorario> grupos = new ArrayList<>();
        List<Horario> horarios = new ArrayList<>();
        List<AsignacionHorario> asignaciones = new ArrayList<>();

        for (DetalleGrupoHorario detalle : detalles) {
            grupos.add(detalle.getGrupoHorario());
        }

        if (!grupos.isEmpty()) {
            asignaciones.addAll(ahc.buscarXGrupos(grupos));
        }

        asignaciones.addAll(ahc.buscarXEmpleado(empleado));

        if (!asignaciones.isEmpty()) {
            for (AsignacionHorario asignacion : asignaciones) {
                horarios.add(asignacion.getHorario());
            }
        }

        return horarios;
    }

    public List<Horario> obtenerHorarios(Empleado empleado, Date fechaInicio, Date fechaFin) {
        //PRIMERO OBTENEMOS LOS GRUPOS HORARIOS ASIGNADOS

        List<DetalleGrupoHorario> detalles = dgc.buscarXEmpleado(empleado, fechaInicio, fechaFin);
        List<GrupoHorario> grupos = new ArrayList<>();
        List<Horario> horarios = new ArrayList<>();
        List<AsignacionHorario> asignaciones = new ArrayList<>();

        for (DetalleGrupoHorario detalle : detalles) {
            grupos.add(detalle.getGrupoHorario());
        }

        if (!grupos.isEmpty()) {
            asignaciones.addAll(ahc.buscarXGrupos(grupos));
        }

        asignaciones.addAll(ahc.buscarXEmpleado(empleado));

        if (!asignaciones.isEmpty()) {
            for (AsignacionHorario asignacion : asignaciones) {
                horarios.add(asignacion.getHorario());
            }
        }

        return horarios;
    }

    private List<RegistroAsistencia> analizarHorario(Empleado empleado, Horario horario, TCAnalisis partida, TCAnalisis llegada) {
        List<RegistroAsistencia> registros = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        Date fInicio = new Date(partida.getFecha().getTime());
        Date fFin = new Date(llegada.getFecha().getTime());

        Date hInicio = new Date(partida.getHora().getTime());
        Date hFin = new Date(llegada.getHora().getTime());

        RegistroAsistencia registro;

        while (fInicio.compareTo(fFin) < 0) {
            //COMAPRAMOS QUE SE ENCUENTRE ENTRE LAS FECHAS DEL HORARIO
            if (fInicio.compareTo(horario.getFechaInicio()) >= 0
                    && fInicio.compareTo(horario.getFechaFin()) <= 0) {
//                System.out.println("FECHA INICIO: " + fInicio.toString() + " FECHA FIN: " + fFin.toString());
                registro = new RegistroAsistencia();
                registro.setFecha(fInicio);
                registro.setEmpleado(empleado);
                registro.setHorario(horario);

                AsignacionPermiso permisoXFecha = this.apc.buscarXDia(empleado, fInicio);

                if (permisoXFecha != null) {
                    //SE GUARDA EL REGISTRO COMO UN PERMISO
                    registro.setPermiso(permisoXFecha.getPermiso());
                    registro.setTipoAsistencia('P');
                } else {
                    Vacacion vacacion = this.vc.buscarXDia(empleado, fInicio);

                    if (vacacion != null) {
                        //SE GUARDA EL REGISTRO COMO VACACION
                        registro.setVacacion(vacacion);
                        registro.setTipoAsistencia('V');

                    } else {

                        boolean diaLaboral = isDiaLaboral(fInicio, horario.getTurnoList().get(0));
                        if (diaLaboral) {
                            //TOMAMOS EN CUENTA QUE SEA FERIADO
                            Feriado feriado = this.fc.buscarXDia(fInicio);
                            if (feriado != null) {
                                //SE REGISTRA COMO FERIADO
                                registro.setFeriado(feriado);
                                registro.setTipoAsistencia('E'); //RECORDAR QUE E PERTENECE A LOS FERIADOS
                            } else {
                                //TOMAMOS EN CUENTA EL ONOMASTICO
                                if (isOnomastico(empleado, fInicio)) {
                                    //SE REGISTRA COMO ONOMASTICO
                                    registro.setPermiso(generarPermisoOnomastico(empleado, fInicio));
                                    registro.setTipoAsistencia('P');
                                } else {
                                    //SE PROCEDE AL ANALISIS DE LA JORNADA
                                    registro = analizarJornada2(empleado, horario.getTurnoList().get(0).getJornada(), fInicio, hInicio, fInicio, fFin, hFin);
                                    if (registro != null) {
                                        registro.setHorario(horario);
                                    }
                                }
                            }
                        } else if (isOnomastico(empleado, fInicio)) {
                            //SE BUSCA EL DIA LABORAL MAS CERCANO PARA ASIGNARLE EL PERMISO POR ONOMASTICO
                            //Y SE AGREGA AL REGISTRO
                        } else {
                            //NO HAY SUCESO SUSCEPTIBLE A REGISTRO
                            registro = null;
                        }
                    }
                }// FIN DEL ELSE PRINCIPAL

                if (registro != null) {
                    registros.add(registro);
                }
            }

            cal.setTime(fInicio);
            cal.add(Calendar.DAY_OF_MONTH, 1);
            fInicio = cal.getTime();

            cal.setTime(hInicio);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            hInicio = cal.getTime();
        }// FIN DEL WHILE
        return registros;
    }

    private boolean isDiaLaboral(Date fInicio, Turno turno) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(fInicio);
        int nDia = cal.get(Calendar.DAY_OF_WEEK);
        //LOS DIAS VAN DESDE EL 1 AL 7 Y CUENTAN DESDE DOMINGO A SABADO
        switch (nDia) {
            case 1:
                return turno.isDomingo();
            case 2:
                return turno.isLunes();
            case 3:
                return turno.isMartes();
            case 4:
                return turno.isMiercoles();
            case 5:
                return turno.isJueves();
            case 6:
                return turno.isViernes();
            case 7:
                return turno.isSabado();
            default:
                return false;
        }
    }

    private boolean isOnomastico(Empleado empleado, Date fInicio) {
        Calendar calEmpleado = Calendar.getInstance();
        Calendar calFecha = Calendar.getInstance();

        calEmpleado.setTime(empleado.getFechaNacimiento());
        calFecha.setTime(fInicio);

        calEmpleado.set(Calendar.YEAR, calFecha.get(Calendar.YEAR));

        return calEmpleado.getTime().compareTo(fInicio) == 0;
    }

//    private long tardanza(Date hora, Date horaTolerancia) {
//        long resultado = hora.getTime() - horaTolerancia.getTime();
//        if (resultado < 0) {
//            return Math.abs(resultado);
//        } else {
//            return 0;
//        }
//    }    
    private RegistroAsistencia analizarJornada2(
            Empleado empleado,
            Jornada jornada,
            Date fecha,
            Date horaInicioAnalisis,
            Date fechaFin,
            Date fechaFinAnalisis,
            Date horaFinAnalisis) {

        RegistroAsistencia registroAsistencia = null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(jornada.getTurnoHS());
        cal.add(Calendar.MINUTE, 60 * 4);

        Date horaMaximaSalida = cal.getTime();
        Date ocupaEntrada = null;
        Date ocupaSalida = null;
        Date ocupaRefrigerio = null;

        if (FechaUtil.compararFechaHora(fecha, horaInicioAnalisis, fecha, horaMaximaSalida) <= 0
                && FechaUtil.compararFechaHora(fechaFinAnalisis, horaFinAnalisis, fechaFin, horaMaximaSalida) >= 0) {
            registroAsistencia = new RegistroAsistencia();
            List<DetalleRegistroAsistencia> detalles = new ArrayList<>();
            char resultadoAsistencia = ' ';
            //ANALIZAMOS PERMISOS

//            System.out.println("PERMISO - EMPLEADO, FECHA, HI, HF: "+empleado.getNroDocumento()+" - "+fecha+" - "+jornada.getTurnoHE()+" - "+jornada.getTurnoHS());
            List<AsignacionPermiso> asignacionesPermiso = this.apc.obtenerPermisosXHora(empleado, fecha, jornada.getTurnoHE(), jornada.getTurnoHS());

//            System.out.println("PERMISOS: "+asignacionesPermiso.size());
            BigDecimal tardanzaPermisos = BigDecimal.ZERO;
            BigDecimal minPermisoSinGoce = BigDecimal.ZERO;
            BigDecimal minPermisoConGoce = BigDecimal.ZERO;
            for (AsignacionPermiso asignacion : asignacionesPermiso) {
                DetalleRegistroAsistencia detallePermiso = this.analizarPermisoXHora(empleado, jornada, asignacion.getPermiso());

                if (detallePermiso.getResultado() != 'F') {
                    if (detallePermiso.getHoraInicio().compareTo(jornada.getTurnoHE()) == 0) {
                        ocupaEntrada = detallePermiso.getHoraFin();
                    }

                    if (detallePermiso.getHoraFin().compareTo(jornada.getTurnoHS()) == 0) {
                        ocupaSalida = detallePermiso.getHoraInicio();
                    }

                    long diferenciaPermisoMilis = detallePermiso.getHoraFin().getTime() - detallePermiso.getHoraInicio().getTime();
                    double diferenciaPermisoMin = diferenciaPermisoMilis / (60 * 1000);

                    if (asignacion.getPermiso().getTipoPermiso().getTipoDescuento() == 'S') { //SI EL TIPO DE DESCUENTO ES SIN GOCE
                        minPermisoSinGoce.add(BigDecimal.valueOf(diferenciaPermisoMin));
                    } else if (asignacion.getPermiso().getTipoPermiso().getTipoDescuento() == 'C') { //SI EL TIPO DE DESCUENTO ES CON GOCE
                        minPermisoConGoce.add(BigDecimal.valueOf(diferenciaPermisoMin));
                    }

                    tardanzaPermisos.add(detallePermiso.getMinTardanza());
                }
                detallePermiso.setRegistroAsistencia(registroAsistencia);
                detalles.add(detallePermiso);

            }
            //ANALIZAMOS TURNO
            //ANALIZAMOS LOS MINUTOS EXTRA TAMBIEN
            BigDecimal minExtra = null;
            DetalleRegistroAsistencia detalleTurno = this.analizarTurno(empleado, fecha, fechaFin, jornada.getDesdeHE(), jornada.getToleranciaHE(), jornada.getTardanzaHE(), jornada.getTurnoHS(), 60 * 4);
            detalleTurno.setHoraInicio((ocupaEntrada == null) ? detalleTurno.getHoraInicio() : ocupaEntrada);
            detalleTurno.setHoraFin((ocupaSalida == null) ? detalleTurno.getHoraFin() : ocupaSalida);
            detalleTurno.setResultado(ocupaEntrada == null && ocupaSalida == null ? detalleTurno.getResultado() : 'F');
            detalleTurno.setMinTardanza(ocupaEntrada == null ? detalleTurno.getMinTardanza() : BigDecimal.ZERO);

            if (detalleTurno.getHoraFin() != null) {
                minExtra = tardanzaMin(detalleTurno.getHoraFin(), jornada.getTurnoHS());
                if (minExtra.compareTo(BigDecimal.ZERO) > 0) {
//                    detalleTurno.setHoraFin(jornada.getTurnoHS());
                    detalleTurno.setMinExtra(minExtra);
                    registroAsistencia.setMinExtra(minExtra);
                }
            }
            //ANALIZAMOS REFRIGERIO 
            //SEGUN DIRECTIVA SOLO SE DEBE ANALIZAR REFRIGERIO SI SE
            //DETECTA UNA SALIDA
            //CASO CONTRARIO NO SE TOMA EN CUENTA
            Long milisInicioRefrigerio = jornada.getRefrigerioHE().getTime() - jornada.getRefrigerioHS().getTime();

            BigDecimal tardanzaRefrigerio = BigDecimal.ZERO;
            long diferenciaRefrigerio = 0;
            char resultadoRefrigerio = 'R';

            if (jornada.isConRefrigerio()) {
                DetalleRegistroAsistencia detalleRefrigerio
                        = this.analizarRefrigerio(
                                empleado,
                                fecha,
                                jornada.getRefrigerioHS(),
                                jornada.getRefrigerioHE(),
                                horaMaximaSalida,
                                milisInicioRefrigerio.intValue(),
                                jornada.getMinRefrigerio());

                detalleTurno.setRegistroAsistencia(registroAsistencia);
                detalleRefrigerio.setRegistroAsistencia(registroAsistencia);

                detalles.add(detalleTurno);
                detalles.add(detalleRefrigerio);

                resultadoAsistencia = detalleRefrigerio.getResultado();
                if (resultadoAsistencia != 'F') {
                    tardanzaRefrigerio = (detalleRefrigerio.getHoraInicio() == null) ? BigDecimal.ZERO : detalleRefrigerio.getMinTardanza();
                    diferenciaRefrigerio = (detalleRefrigerio.getHoraInicio() == null) ? 60 * 1000 * 60 : detalleRefrigerio.getHoraFin().getTime() - detalleRefrigerio.getHoraInicio().getTime();
                }
            }

            if (detalleTurno.getResultado() == 'F' || resultadoRefrigerio == 'F') {
                resultadoAsistencia = 'F';
            } else if (detalleTurno.getResultado() == 'T' || resultadoRefrigerio == 'F') {
                resultadoAsistencia = 'T';
            } else if (detalleTurno.getResultado() == 'R' && resultadoRefrigerio == 'R') {
                resultadoAsistencia = 'R';
            }

            BigDecimal tardanzaTotal = (resultadoAsistencia != 'F') ? detalleTurno.getMinTardanza()
                    .add(tardanzaRefrigerio)
                    .add(tardanzaPermisos) : null;
            BigDecimal trabajoTotal = null;

            if (resultadoAsistencia != 'F') {
                long diferenciaTurnoMilis = detalleTurno.getHoraFin().getTime() - detalleTurno.getHoraInicio().getTime();
                long diferenciaRefrigerioMilis = diferenciaRefrigerio;

                long diferenciaTrabajoMilis = diferenciaTurnoMilis - diferenciaRefrigerioMilis;

                double diferenciaTrabajoMin = diferenciaTrabajoMilis / (60 * 1000);

                trabajoTotal = BigDecimal.valueOf(diferenciaTrabajoMin).subtract(tardanzaTotal).subtract(minPermisoSinGoce);

            }

            registroAsistencia.setTipoAsistencia(resultadoAsistencia);
//            System.out.println("TARDANZA TOTAL: " + tardanzaTotal);
            registroAsistencia.setMinTardanza(tardanzaTotal);
            registroAsistencia.setMinTrabajados(trabajoTotal);
            registroAsistencia.setDetalleRegistroAsistenciaList(detalles);
            registroAsistencia.setFecha(fecha);
            registroAsistencia.setEmpleado(empleado);

        }

        return registroAsistencia;
    }

    public BigDecimal tardanzaMin(Date horaMarcada, Date horaComparar) {
        Long diferencia = horaMarcada.getTime() - horaComparar.getTime();
        if (diferencia > 0) {
//            System.out.println("MINUTOS: "+Double.parseDouble(diferencia+"")/(1000 * 60));
            double diferenciaMin = diferencia / (60 * 1000);
            return BigDecimal.valueOf(diferenciaMin);
        } else {
            return BigDecimal.ZERO;
        }
    }

    private DetalleRegistroAsistencia analizarTurno(Empleado empleado, Date fechaInicio, Date fechaFin, Date horaDesde, Date horaToleranciaInicio, Date horaMaximaInicio, Date horaFin, int minutosMaximoFin) {
        DetalleRegistroAsistencia registroTurno = new DetalleRegistroAsistencia();
        registroTurno.setOrden(0);
        registroTurno.setTipoRegistro('T');

        Calendar calendar = Calendar.getInstance();

        Marcacion marcacionInicio = mc.buscarXFechaXhora(empleado, fechaInicio, horaDesde, horaMaximaInicio);

        char resultadoInicio;
        char resultadoFin;

        if (marcacionInicio == null) {
            registroTurno.setHoraInicio(null);
            resultadoInicio = 'F';
        } else {
            BigDecimal tardanzaMin = tardanzaMin(FechaUtil.soloHora(marcacionInicio.getFechaHora()), horaToleranciaInicio);
            registroTurno.setHoraInicio(FechaUtil.soloHora(marcacionInicio.getFechaHora()));
            if (tardanzaMin.compareTo(BigDecimal.ZERO) > 0) {
                resultadoInicio = 'T';
            } else {
                resultadoInicio = 'R';
            }
            registroTurno.setMinTardanza(tardanzaMin);
        }

        calendar.setTime(horaFin);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        Marcacion marcacionFin = mc.buscarXFechaXhora(empleado, fechaFin, horaFin, calendar.getTime());

        if (marcacionFin == null) {
            registroTurno.setHoraFin(null);
            resultadoFin = 'F';
        } else {
            resultadoFin = 'R';
            registroTurno.setHoraFin(FechaUtil.soloHora(marcacionFin.getFechaHora()));
        }

        if (resultadoInicio == 'F' || resultadoFin == 'F') {
            registroTurno.setResultado('F');
        } else {
            registroTurno.setResultado(resultadoInicio);
        }

        return registroTurno;
    }

    private DetalleRegistroAsistencia analizarRefrigerio(Empleado empleado, Date fechaInicio, Date horaInicio, Date horaFin, Date horaMaximaFin, int milisHoraInicio, int minutosRefrigerio) {
        DetalleRegistroAsistencia registroRefrigerio = new DetalleRegistroAsistencia();
        registroRefrigerio.setOrden(1);
        registroRefrigerio.setTipoRegistro('R');

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(horaInicio);
        calendar.add(Calendar.MILLISECOND, milisHoraInicio);

        Date horaMaximaInicio = calendar.getTime();

        System.out.println("MINUTOS HORA INICIO: " + milisHoraInicio + " " + horaMaximaInicio);
//        System.out.println("MARCACION INICIO REFRIGERIO PARAMS: " + fechaInicio + " " + horaInicio + " " + horaMaximaInicio);
        Marcacion marcacionInicio = mc.buscarXFechaXhora(empleado, fechaInicio, horaInicio, horaMaximaInicio);

        if (marcacionInicio == null) {
            //NO SE TOMA EN CUENTA EL REFRIGERIO
            registroRefrigerio.setHoraInicio(null);
            registroRefrigerio.setHoraFin(null);
            registroRefrigerio.setResultado('R');
        } else {
            registroRefrigerio.setHoraInicio(FechaUtil.soloHora(marcacionInicio.getFechaHora()));
            calendar.setTime(FechaUtil.soloHora(marcacionInicio.getFechaHora()));
            calendar.add(Calendar.MINUTE, minutosRefrigerio);
            Date horaEsperadaFin;
            if (horaFin.before(calendar.getTime())) {
                horaEsperadaFin = horaFin;
            } else {
                horaEsperadaFin = calendar.getTime();
            }

            calendar.setTime(FechaUtil.soloHora(marcacionInicio.getFechaHora()));
            calendar.add(Calendar.SECOND, 1);
            Date limiteInferiorHoraFin = calendar.getTime();

            Marcacion marcacionFin = mc.buscarXFechaXhora(empleado, fechaInicio, limiteInferiorHoraFin, horaMaximaFin);

            if (marcacionFin == null) {
                registroRefrigerio.setHoraFin(null);
                registroRefrigerio.setResultado('F');
            } else {
//                System.out.println("HORA FIN, HORA ESPERADA FIN: "+marcacionFin.getHora()+" "+horaEsperadaFin);
                BigDecimal minTardanza = this.tardanzaMin(FechaUtil.soloHora(marcacionFin.getFechaHora()), horaEsperadaFin);
                registroRefrigerio.setHoraFin(FechaUtil.soloHora(marcacionFin.getFechaHora()));
                registroRefrigerio.setMinTardanza(minTardanza);

                if (minTardanza.compareTo(BigDecimal.ZERO) > 0) {
                    registroRefrigerio.setResultado('T');
                } else {
                    registroRefrigerio.setResultado('R');
                }
            }
        }

        return registroRefrigerio;
    }//FIN DE ANALIZAR REFRIGERIO

    private DetalleRegistroAsistencia analizarPermisoXHora(Empleado empleado, Jornada jornada, Permiso permiso) {
        DetalleRegistroAsistencia registroPermiso = new DetalleRegistroAsistencia();
        registroPermiso.setOrden(2);
        registroPermiso.setTipoRegistro('P');
        Calendar cal = Calendar.getInstance();
        Date horaInicio = null;
        Date horaFin = null;

        Long diferenciaPermiso = permiso.getHoraFin().getTime() - permiso.getHoraInicio().getTime();

        //VERIFICAMOS SI OCUPA O NO HORA DE ENTRADA U HORA DE SALIDA
        if (permiso.getHoraInicio().equals(jornada.getTurnoHE())) {
            horaInicio = jornada.getTurnoHE();
        } else {
            //BUSCAMOS MARCACION
            cal.setTime(permiso.getHoraInicio());
            cal.add(Calendar.MINUTE, MIN_ANTES_INICIO_PERMISO);
            Date limiteSuperiorHoraInicio = cal.getTime();

            Marcacion inicioPermiso = mc.buscarXFechaXhora(empleado, permiso.getFechaInicio(), permiso.getHoraInicio(), limiteSuperiorHoraInicio);

            horaInicio = (inicioPermiso == null) ? null : FechaUtil.soloHora(inicioPermiso.getFechaHora());
        }

        BigDecimal tardanzaEntradaPermiso = null;

        if (horaInicio != null) {
            cal.setTime(horaInicio);
            cal.add(Calendar.MINUTE, 1);

            Date limiteInferiorFinPermiso = cal.getTime();

            cal.setTime(horaInicio);
            cal.add(Calendar.MILLISECOND, diferenciaPermiso.intValue());
            Date horaEsperadaFinPermiso = cal.getTime();

            Marcacion finPermiso = mc.buscarXFechaXhora(empleado, permiso.getFechaInicio(), limiteInferiorFinPermiso, jornada.getTurnoHS());

            horaFin = (finPermiso == null) ? jornada.getTurnoHS() : FechaUtil.soloHora(finPermiso.getFechaHora());

            tardanzaEntradaPermiso = (horaFin == null) ? null : tardanzaMin(horaFin, horaEsperadaFinPermiso);
        }

        registroPermiso.setHoraInicio(horaInicio);
        registroPermiso.setHoraFin(horaFin);
        registroPermiso.setMinTardanza(tardanzaEntradaPermiso);
        registroPermiso.setPermiso(permiso);
        char resultado = ' ';
        if (horaInicio == null || horaFin == null) {
            resultado = 'F';
        } else if (tardanzaEntradaPermiso.compareTo(BigDecimal.ZERO) > 0) {
            resultado = 'T';
        } else {
            resultado = 'R';
        }
        registroPermiso.setResultado(resultado);

        return registroPermiso;
    }

    private Permiso generarPermisoOnomastico(Empleado empleado, Date fInicio) {
        pc.prepararCrear();
        Permiso onomastico = pc.getSeleccionado();

        //CREAMOS LA ASIGNACION
        AsignacionPermiso ap = new AsignacionPermiso();
        ap.setEmpleado(empleado);
        ap.setPermiso(onomastico);

        //
        onomastico.getAsignacionPermisoList().add(ap);
        onomastico.setFechaInicio(fInicio);
        onomastico.setFechaFin(fInicio);
        onomastico.setPorFecha(true);
        onomastico.setTipoPermiso(tpc.buscarPorId("ONO"));
        onomastico.setOpcion('F');
        onomastico.setMotivo("LICENCIA POR ONOMÁSTICO");
        onomastico.setDocumento("LICENCIA POR ONOMÁSTICO");

        long diferencia = onomastico.getFechaFin().getTime() - onomastico.getFechaInicio().getTime();
        BigDecimal diferenciaMin = new BigDecimal(diferencia / (60 * 1000 * 60));
        onomastico.setDiferencia(diferenciaMin);

        if (pc.accion(Controlador.NUEVO)) {
            LOG.info("SE GUARDO EL PERMISO POR ONOMASTICO");
        } else {
            LOG.info("HUBO UN ERROR");
        }

        pc.getDao().getEntityManager();

        return onomastico;
    }

}
