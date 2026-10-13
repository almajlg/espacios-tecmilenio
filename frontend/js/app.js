/**
 * Campus Espacios - Universidad Tecmilenio
 * Lógica de Cliente y Comunicación con API Backend Java
 */

// Configuración de endpoints (soporta relativo y localhost:8080)
const API_BASE = window.location.origin.includes('http') && !window.location.origin.includes('file:')
    ? `${window.location.origin}/api`
    : 'http://localhost:8080/api';

// Estado local
const state = {
    espacios: [],
    usuarios: [],
    reservas: [],
    stats: null,
    activeTab: 'dashboard',
    espacioFilter: 'all',
    searchQuery: '',
    backendConnected: false,
    agendaMode: 'table',
    timelineDate: ''
};

// ====================================================================
// INICIALIZACIÓN
// ====================================================================
document.addEventListener('DOMContentLoaded', () => {
    initDateDisplay();
    setupNavigation();
    setupModals();
    setupSearchAndFilters();
    setupRealtimeConflictCheck();
    
    // Cargar datos del Backend
    loadAllData();

    // Actualización periódica cada 30 segundos
    setInterval(loadAllData, 30000);
});

function initDateDisplay() {
    const today = new Date();
    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
    const dateFormatted = today.toLocaleDateString('es-ES', options);
    const elem = document.getElementById('currentDateDisplay');
    if (elem) {
        elem.textContent = dateFormatted.charAt(0).toUpperCase() + dateFormatted.slice(1);
    }

    // Configurar fechas por defecto en inputs
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    const todayIso = `${yyyy}-${mm}-${dd}`;

    state.timelineDate = todayIso;

    const reservaFechaInput = document.getElementById('reservaFecha');
    if (reservaFechaInput) reservaFechaInput.value = todayIso;

    const aldeaDateSelector = document.getElementById('aldeaDateSelector');
    if (aldeaDateSelector) aldeaDateSelector.value = todayIso;

    const edificio1DateSelector = document.getElementById('edificio1DateSelector');
    if (edificio1DateSelector) edificio1DateSelector.value = todayIso;

    const edificio2DateSelector = document.getElementById('edificio2DateSelector');
    if (edificio2DateSelector) edificio2DateSelector.value = todayIso;
}

// Helpers de clasificación de edificios
// REGLA DEL USUARIO:
// 1) Edificio 1 (Preparatoria): salones que empiezan con 1 (ej. 1102, 1103, 1104, 1201, 1310).
// 2) Edificio 2 (Profesional): salones que empiezan con 22__ a 24__ (ej. 2201-2205, 2302-2306, 2404-2405).
// 3) Aldea: salones que empiezan con 21__ (2101-2113).
function isAldea(e) {
    if (!e) return false;
    if (e.tipoEspacio === 'Aldea') return true;
    const nom = (e.nombreEspacio || '').trim();
    if (/^21\d{2}/.test(nom)) return true;
    if (nom.toLowerCase().includes('aldea')) return true;
    return false;
}

function isEdificio2(e) {
    if (!e) return false;
    if (isAldea(e)) return false;
    if (e.tipoEspacio === 'Laboratorio' || e.tipoEspacio === 'Cancha' || e.tipoEspacio === 'Otro') return false;
    if (e.tipoEspacio === 'Edificio2') return true;
    const nom = (e.nombreEspacio || '').trim();
    if (/^(2[2-9]|3[0-9])\d{2}/.test(nom)) return true;
    if (e.edificio && (e.edificio.includes('Edificio 2') || e.edificio.includes('Profesional'))) return true;
    return false;
}

function isEdificio1(e) {
    if (!e) return false;
    if (isAldea(e) || isEdificio2(e)) return false;
    if (e.tipoEspacio === 'Laboratorio' || e.tipoEspacio === 'Cancha' || e.tipoEspacio === 'Otro') return false;
    if (e.tipoEspacio === 'Edificio1') return true;
    const nom = (e.nombreEspacio || '').trim();
    if (/^1\d{3}/.test(nom)) return true;
    if (e.edificio && (e.edificio.includes('Edificio 1') || e.edificio.includes('Preparatoria'))) return true;
    return false;
}

function isMezanine(e) {
    if (!e) return false;
    const nom = (e.nombreEspacio || '').toLowerCase();
    return nom.includes('mezanine');
}

function isWeekdayDate(fechaStr) {
    if (!fechaStr) return false;
    const parts = fechaStr.split('-');
    if (parts.length < 3) return false;
    const d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
    const day = d.getDay(); // 0: Dom, 1: Lun, 2: Mar, 3: Mié, 4: Jue, 5: Vie, 6: Sáb
    return day >= 1 && day <= 5;
}

function getEspacioEdificioLabel(e) {
    if (!e) return 'General';
    if (isMezanine(e)) return 'Mezanine';
    if (isEdificio1(e)) return 'Edificio 1 (Prepa)';
    if (isEdificio2(e)) return 'Edificio 2 (Prof)';
    if (isAldea(e)) return 'Aldea';
    if (e.tipoEspacio === 'Laboratorio') return 'Laboratorio';
    if (e.tipoEspacio === 'Cancha') return 'Cancha';
    if (e.tipoEspacio === 'Otro') return 'Otros';
    return e.tipoEspacio || 'General';
}

// ====================================================================
// CARGA Y SINCRONIZACIÓN DE DATOS CON EL BACKEND EN JAVA
// ====================================================================
async function loadAllData() {
    try {
        const [espaciosRes, usuariosRes, reservasRes, statsRes] = await Promise.all([
            fetch(`${API_BASE}/espacios`),
            fetch(`${API_BASE}/usuarios`),
            fetch(`${API_BASE}/reservas`),
            fetch(`${API_BASE}/stats`)
        ]);

        if (espaciosRes.ok && usuariosRes.ok && reservasRes.ok) {
            state.espacios = await espaciosRes.json();
            state.usuarios = await usuariosRes.json();
            state.reservas = await reservasRes.json();
            if (statsRes.ok) state.stats = await statsRes.json();
            
            setBackendStatus(true);
            renderAllViews();
        } else {
            throw new Error('Respuesta no exitosa del servidor');
        }
    } catch (err) {
        console.warn('Backend Java desconectado o no disponible en 8080:', err.message);
        setBackendStatus(false);
        // Cargar datos locales de respaldo si el backend aún no corre
        loadMockFallbackData();
    }
}

function setBackendStatus(connected) {
    state.backendConnected = connected;
    const statusElem = document.getElementById('connectionStatus');
    if (!statusElem) return;

    if (connected) {
        statusElem.innerHTML = `<span class="status-dot"></span><span class="status-label">Sistema Conectado</span>`;
        statusElem.style.color = '#68c143';
    } else {
        statusElem.innerHTML = `<span class="status-dot" style="background-color:#f59e0b;box-shadow:0 0 8px #f59e0b"></span><span class="status-label">Modo Local (Inicia backend)</span>`;
        statusElem.style.color = '#fbbf24';
    }
}

// ====================================================================
// RENDERIZADO GENERAL DE VISTAS
// ====================================================================
function renderAllViews() {
    renderDashboard();
    renderAgenda();
    renderEdificio1View();
    renderEdificio2View();
    renderAldeaView();
    renderEspaciosGrid();
    renderUsuarios();
    populateSelects();
    updateBadges();
}

function updateBadges() {
    const badgeTotal = document.getElementById('badgeTotalEspacios');
    if (badgeTotal) badgeTotal.textContent = state.espacios.length;

    const edif1Count = state.espacios.filter(isEdificio1).length;
    const badgeEdif1 = document.getElementById('badgeEdificio1');
    if (badgeEdif1) badgeEdif1.textContent = edif1Count;

    const edif2Count = state.espacios.filter(isEdificio2).length;
    const badgeEdif2 = document.getElementById('badgeEdificio2');
    if (badgeEdif2) badgeEdif2.textContent = edif2Count;

    const aldeaCount = state.espacios.filter(isAldea).length;
    const badgeAldea = document.getElementById('badgeAldea');
    if (badgeAldea) badgeAldea.textContent = aldeaCount;
}

// ====================================================================
// 1. DASHBOARD
// ====================================================================
function renderDashboard() {
    // Totales
    const totalEspacios = state.espacios.length;
    const edif1Espacios = state.espacios.filter(isEdificio1).length;
    const edif2Espacios = state.espacios.filter(isEdificio2).length;
    const aldeaEspacios = state.espacios.filter(isAldea).length;
    const otrosEspacios = state.espacios.filter(e => e.tipoEspacio === 'Otro' || e.tipoEspacio === 'Cancha' || e.tipoEspacio === 'Laboratorio').length;

    const activas = state.reservas.filter(r => r.estadoReserva === 'Activa').length;
    
    // Ocupados hoy
    const today = new Date().toISOString().split('T')[0];
    const ocupadosHoy = new Set(
        state.reservas
            .filter(r => r.estadoReserva === 'Activa' && r.fechaReserva === today)
            .map(r => r.idEspacio)
    ).size;

    if (document.getElementById('cardTotalEspacios')) document.getElementById('cardTotalEspacios').textContent = totalEspacios;
    if (document.getElementById('cardEdificio1Espacios')) document.getElementById('cardEdificio1Espacios').textContent = edif1Espacios;
    if (document.getElementById('cardEdificio2Espacios')) document.getElementById('cardEdificio2Espacios').textContent = edif2Espacios;
    if (document.getElementById('cardAldeaEspacios')) document.getElementById('cardAldeaEspacios').textContent = aldeaEspacios;
    if (document.getElementById('cardOtrosEspacios')) document.getElementById('cardOtrosEspacios').textContent = otrosEspacios;

    if (document.getElementById('statOcupadosHoy')) document.getElementById('statOcupadosHoy').textContent = ocupadosHoy;
    if (document.getElementById('statReservasActivas')) document.getElementById('statReservasActivas').textContent = activas;

    // Próximas reservas (top 8 ordenadas)
    const tbody = document.getElementById('tbodyProximasReservas');
    if (tbody) {
        tbody.innerHTML = '';

        const proximas = state.reservas
            .filter(r => r.estadoReserva === 'Activa')
            .slice(0, 8);

        if (proximas.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;color:var(--text-muted);padding:24px;">No hay clases ni eventos activos programados.</td></tr>`;
        } else {
            proximas.forEach(r => {
                const tr = document.createElement('tr');
                const esp = state.espacios.find(e => e.idEspacio === r.idEspacio);
                const ubicacion = esp ? getEspacioEdificioLabel(esp) : (r.edificioEspacio || r.tipoEspacio || 'General');

                // Si hay profesor en observaciones o solicitante
                const profMatch = (r.observaciones || '').match(/Profesor:\s*([^|;\n]+)/i);
                const profNombre = profMatch ? profMatch[1].trim() : (r.rolUsuario === 'Profesor' ? r.nombreUsuario : null);

                tr.innerHTML = `
                    <td>
                        <strong>${formatFecha(r.fechaReserva)}</strong><br>
                        <small style="color:var(--text-muted)"><i class="ph-bold ph-clock"></i> ${r.horaInicio} - ${r.horaFin}</small>
                    </td>
                    <td><strong style="color:var(--tecmilenio-green);">${r.nombreEspacio}</strong></td>
                    <td><span class="badge-type ${isEdificio1(esp) ? 'edificio1' : isEdificio2(esp) ? 'edificio2' : isAldea(esp) ? 'aldea' : (r.tipoEspacio || '').toLowerCase()}">${ubicacion}</span></td>
                    <td>
                        <strong>${escapeHtml(r.motivoEvento)}</strong>
                        ${r.servicioMedico ? `<span class="badge-status" style="display:inline-flex;align-items:center;gap:3px;background:#fee2e2;color:#b91c1c;border:1px solid #fca5a5;padding:1px 6px;border-radius:10px;font-size:0.7rem;margin-left:6px;"><i class="ph-bold ph-first-aid"></i> Servicio Médico</span>` : ''}
                        ${r.servicioTI ? `<span class="badge-status" style="display:inline-flex;align-items:center;gap:3px;background:#dbeafe;color:#1e40af;border:1px solid #93c5fd;padding:1px 6px;border-radius:10px;font-size:0.7rem;margin-left:6px;"><i class="ph-bold ph-laptop"></i> Soporte TI</span>` : ''}
                        ${r.archivoLayout ? `<a href="${r.archivoLayout}" target="_blank" class="badge-status" style="display:inline-flex;align-items:center;gap:3px;background:#e0f2fe;color:#0369a1;border:1px solid #7dd3fc;padding:1px 6px;border-radius:10px;font-size:0.7rem;margin-left:6px;text-decoration:none;" title="Ver archivo de layout adjunto: ${escapeHtml(r.archivoLayoutNombre || '')}"><i class="ph-bold ph-file-arrow-up"></i> Layout</a>` : ''}
                        ${profNombre ? `<div style="font-size:0.75rem;color:var(--text-muted);margin-top:2px;"><i class="ph-bold ph-chalkboard-teacher"></i> Docente: ${escapeHtml(profNombre)}</div>` : ''}
                    </td>
                    <td>
                        <div>${escapeHtml(r.nombreUsuario || 'Responsable')}</div>
                        <small style="color:var(--text-muted)">${escapeHtml(r.rolUsuario || 'Colaborador')}</small>
                    </td>
                    <td>
                        <div style="display:flex;align-items:center;justify-content:space-between;gap:6px;">
                            <span class="badge-status ${r.estadoReserva.toLowerCase()}">${r.estadoReserva}</span>
                            <div style="display:flex;gap:3px;">
                                <button class="btn-icon" style="color:#1d4ed8;background:#eff6ff;width:26px;height:26px;font-size:0.85rem;" title="Descargar Reporte Word (.doc con Logo)" onclick="descargarReporteWord(${r.idReserva})">
                                    <i class="ph-bold ph-file-doc"></i>
                                </button>
                                <button class="btn-icon" style="color:#15803d;background:#f0fdf4;width:26px;height:26px;font-size:0.85rem;" title="Descargar Ficha Excel (.csv)" onclick="descargarReporteExcelIndividual(${r.idReserva})">
                                    <i class="ph-bold ph-microsoft-excel-logo"></i>
                                </button>
                            </div>
                        </div>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        }
    }

    // Mini Grid Edificio 1 (Preparatoria: 1102-1310)
    const miniGridEdif1 = document.getElementById('gridMiniEdificio1');
    if (miniGridEdif1) {
        miniGridEdif1.innerHTML = '';
        const edif1Rooms = state.espacios
            .filter(isEdificio1)
            .sort((a, b) => a.nombreEspacio.localeCompare(b.nombreEspacio, undefined, { numeric: true }));

        edif1Rooms.forEach(e => {
            const chip = document.createElement('div');
            chip.className = 'edif1-chip';
            chip.textContent = e.nombreEspacio;
            chip.title = `Salón ${e.nombreEspacio} (Edificio 1 - Preparatoria) - Capacidad: ${e.capacidad} personas. Clic para agendar.`;
            chip.onclick = () => openReservaParaEspacio(e.idEspacio);
            miniGridEdif1.appendChild(chip);
        });
    }

    // Mini Grid Edificio 2 (Profesional: 2201-2405)
    const miniGridEdif2 = document.getElementById('gridMiniEdificio2');
    if (miniGridEdif2) {
        miniGridEdif2.innerHTML = '';
        const edif2Rooms = state.espacios
            .filter(isEdificio2)
            .sort((a, b) => a.nombreEspacio.localeCompare(b.nombreEspacio, undefined, { numeric: true }));

        edif2Rooms.forEach(e => {
            const chip = document.createElement('div');
            chip.className = 'edif2-chip';
            chip.textContent = e.nombreEspacio;
            chip.title = `Salón ${e.nombreEspacio} (Edificio 2 - Profesional) - Capacidad: ${e.capacidad} personas. Clic para agendar.`;
            chip.onclick = () => openReservaParaEspacio(e.idEspacio);
            miniGridEdif2.appendChild(chip);
        });
    }

    // Mini Grid Aldea (2101-2113)
    const miniGridAldea = document.getElementById('gridMiniAldea');
    if (miniGridAldea) {
        miniGridAldea.innerHTML = '';
        const aldeaRooms = state.espacios
            .filter(isAldea)
            .sort((a, b) => a.nombreEspacio.localeCompare(b.nombreEspacio, undefined, { numeric: true }));

        aldeaRooms.forEach(e => {
            const chip = document.createElement('div');
            chip.className = 'aldea-chip';
            chip.textContent = e.nombreEspacio;
            chip.title = `Salón ${e.nombreEspacio} (Aldea) - Capacidad: ${e.capacidad} personas. Clic para agendar.`;
            chip.onclick = () => openReservaParaEspacio(e.idEspacio);
            miniGridAldea.appendChild(chip);
        });
    }
}

// ====================================================================
// 2. AGENDA Y CALENDARIO
// ====================================================================
function renderAgenda() {
    const tbody = document.getElementById('tbodyAgendaCompleta');
    if (!tbody) return;
    tbody.innerHTML = '';

    const fechaFiltro = document.getElementById('filterFecha') ? document.getElementById('filterFecha').value : '';
    const tipoFiltro = document.getElementById('filterTipoEspacio') ? document.getElementById('filterTipoEspacio').value : '';
    const estadoFiltro = document.getElementById('filterEstado') ? document.getElementById('filterEstado').value : '';

    let filtradas = state.reservas;

    if (fechaFiltro) {
        filtradas = filtradas.filter(r => r.fechaReserva === fechaFiltro);
    }
    if (tipoFiltro) {
        if (tipoFiltro === 'Edificio1') {
            filtradas = filtradas.filter(r => {
                const esp = state.espacios.find(e => e.idEspacio === r.idEspacio);
                return isEdificio1(esp);
            });
        } else if (tipoFiltro === 'Edificio2') {
            filtradas = filtradas.filter(r => {
                const esp = state.espacios.find(e => e.idEspacio === r.idEspacio);
                return isEdificio2(esp);
            });
        } else if (tipoFiltro === 'Aldea') {
            filtradas = filtradas.filter(r => {
                const esp = state.espacios.find(e => e.idEspacio === r.idEspacio);
                return isAldea(esp);
            });
        } else if (tipoFiltro === 'Mezanine') {
            filtradas = filtradas.filter(r => {
                const esp = state.espacios.find(e => e.idEspacio === r.idEspacio);
                return isMezanine(esp);
            });
        } else {
            filtradas = filtradas.filter(r => r.tipoEspacio === tipoFiltro && !isMezanine(state.espacios.find(e => e.idEspacio === r.idEspacio)));
        }
    }
    if (estadoFiltro) {
        filtradas = filtradas.filter(r => r.estadoReserva === estadoFiltro);
    }
    if (state.searchQuery) {
        const q = state.searchQuery.toLowerCase();
        filtradas = filtradas.filter(r => 
            (r.nombreEspacio && r.nombreEspacio.toLowerCase().includes(q)) ||
            (r.motivoEvento && r.motivoEvento.toLowerCase().includes(q)) ||
            (r.nombreUsuario && r.nombreUsuario.toLowerCase().includes(q)) ||
            (r.observaciones && r.observaciones.toLowerCase().includes(q))
        );
    }

    const lblRegistros = document.getElementById('lblTotalRegistros');
    if (lblRegistros) lblRegistros.textContent = `${filtradas.length} reservas registradas`;

    if (filtradas.length === 0) {
        tbody.innerHTML = `<tr><td colspan="10" style="text-align:center;color:var(--text-muted);padding:32px;">No se encontraron reservas con los filtros aplicados.</td></tr>`;
        return;
    }

    filtradas.forEach(r => {
        const esp = state.espacios.find(e => e.idEspacio === r.idEspacio);
        const ubicacion = esp ? getEspacioEdificioLabel(esp) : (r.edificioEspacio || r.tipoEspacio || 'General');

        // Extraer docente si viene en observaciones
        const profMatch = (r.observaciones || '').match(/Profesor:\s*([^|;\n]+)/i);
        const docenteNombre = profMatch ? profMatch[1].trim() : null;

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>#${r.idReserva}</td>
            <td><strong>${formatFecha(r.fechaReserva)}</strong></td>
            <td><strong>${r.horaInicio} - ${r.horaFin}</strong></td>
            <td><strong style="color:var(--tecmilenio-green);font-size:1.02rem;">${r.nombreEspacio}</strong></td>
            <td><span class="badge-type ${isMezanine(esp) ? 'mezanine' : isEdificio1(esp) ? 'edificio1' : isEdificio2(esp) ? 'edificio2' : isAldea(esp) ? 'aldea' : (r.tipoEspacio || '').toLowerCase()}">${ubicacion}</span></td>
            <td>
                <strong>${escapeHtml(r.motivoEvento)}</strong>
                ${r.servicioMedico ? `<div style="margin-top:3px;"><span class="badge-status" style="display:inline-flex;align-items:center;gap:3px;background:#fee2e2;color:#b91c1c;border:1px solid #fca5a5;padding:2px 7px;border-radius:10px;font-size:0.72rem;font-weight:600;"><i class="ph-bold ph-first-aid"></i> Servicio Médico Requerido</span></div>` : ''}
                ${r.servicioTI ? `<div style="margin-top:3px;"><span class="badge-status" style="display:inline-flex;align-items:center;gap:3px;background:#dbeafe;color:#1e40af;border:1px solid #93c5fd;padding:2px 7px;border-radius:10px;font-size:0.72rem;font-weight:600;"><i class="ph-bold ph-laptop"></i> Soporte TI Requerido</span></div>` : ''}
                ${r.archivoLayout ? `<div style="margin-top:3px;"><a href="${r.archivoLayout}" target="_blank" class="badge-status" style="display:inline-flex;align-items:center;gap:3px;background:#e0f2fe;color:#0369a1;border:1px solid #7dd3fc;padding:2px 7px;border-radius:10px;font-size:0.72rem;font-weight:600;text-decoration:none;" title="Abrir archivo adjunto"><i class="ph-bold ph-paperclip"></i> Layout: ${escapeHtml(r.archivoLayoutNombre || 'Ver archivo')}</a></div>` : ''}
                ${docenteNombre ? `<div style="font-size:0.75rem;color:var(--tecmilenio-green);font-weight:600;margin-top:2px;"><i class="ph-bold ph-chalkboard-teacher"></i> Docente: ${escapeHtml(docenteNombre)}</div>` : ''}
            </td>
            <td>
                <div>${escapeHtml(r.nombreUsuario || 'Responsable')}</div>
                <small style="color:var(--text-muted)">${escapeHtml(r.rolUsuario || 'Colaborador')}</small>
            </td>
            <td><small>${escapeHtml(r.observaciones || '-')}</small></td>
            <td><span class="badge-status ${r.estadoReserva.toLowerCase()}">${r.estadoReserva}</span></td>
            <td>
                <div style="display:flex;gap:5px;align-items:center;">
                    <button class="btn-icon" style="color:#1d4ed8;background:#eff6ff;width:30px;height:30px;" title="Descargar Reporte Individual en Word (.doc con Logo)" onclick="descargarReporteWord(${r.idReserva})">
                        <i class="ph-bold ph-file-doc"></i>
                    </button>
                    <button class="btn-icon" style="color:#15803d;background:#f0fdf4;width:30px;height:30px;" title="Descargar Ficha Individual en Excel (.csv)" onclick="descargarReporteExcelIndividual(${r.idReserva})">
                        <i class="ph-bold ph-microsoft-excel-logo"></i>
                    </button>
                    <button class="btn-icon" style="color:var(--tecmilenio-green);width:30px;height:30px;" title="Editar Reserva" onclick="abrirEditarReserva(${r.idReserva})">
                        <i class="ph-bold ph-pencil-simple"></i>
                    </button>
                    ${r.estadoReserva === 'Activa' ? `
                        <button class="btn-icon" style="color:#005934;width:30px;height:30px;" title="Marcar como Completada" onclick="cambiarEstadoReserva(${r.idReserva}, 'Completada')">
                            <i class="ph-bold ph-check"></i>
                        </button>
                        <button class="btn-icon" style="color:var(--danger);width:30px;height:30px;" title="Cancelar Reserva" onclick="cambiarEstadoReserva(${r.idReserva}, 'Cancelada')">
                            <i class="ph-bold ph-x"></i>
                        </button>
                    ` : ''}
                    <button class="btn-icon" style="color:var(--text-muted);width:30px;height:30px;" title="Eliminar del Registro" onclick="eliminarReserva(${r.idReserva})">
                        <i class="ph-bold ph-trash"></i>
                    </button>
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });

    renderTimelineGrid();
}

// ====================================================================
// 3. VISTA DEDICADA: EDIFICIO 1 (PREPARATORIA)
// ====================================================================
function renderEdificio1View() {
    const grid = document.getElementById('gridEdificio1Dedicada');
    if (!grid) return;
    grid.innerHTML = '';

    const salones = state.espacios
        .filter(isEdificio1)
        .sort((a, b) => a.nombreEspacio.localeCompare(b.nombreEspacio, undefined, { numeric: true }));

    const dateVal = document.getElementById('edificio1DateSelector') ? document.getElementById('edificio1DateSelector').value : new Date().toISOString().split('T')[0];

    if (salones.length === 0) {
        grid.innerHTML = `<div style="grid-column: 1/-1;text-align:center;padding:32px;color:var(--text-muted);">No hay salones registrados en el Edificio 1 (Preparatoria).</div>`;
        return;
    }

    salones.forEach(e => {
        const reservasHoy = state.reservas.filter(r => 
            r.idEspacio === e.idEspacio && 
            r.fechaReserva === dateVal && 
            r.estadoReserva === 'Activa'
        );

        const card = document.createElement('div');
        card.className = 'espacio-card is-edif1';
        card.innerHTML = `
            <div class="espacio-header">
                <div>
                    <h3 class="espacio-name">Salón ${e.nombreEspacio}</h3>
                    <span class="badge-type edificio1">🏢 Edificio 1 (Preparatoria)</span>
                </div>
                <span class="badge-status ${reservasHoy.length === 0 ? 'activa' : 'cancelada'}">
                    ${reservasHoy.length === 0 ? 'Libre' : `${reservasHoy.length} en uso`}
                </span>
            </div>

            <div class="espacio-details">
                <div class="detail-row">
                    <i class="ph-bold ph-users"></i>
                    <span>Capacidad: <strong>${e.capacidad} estudiantes</strong></span>
                </div>
                <div class="detail-row">
                    <i class="ph-bold ph-clock"></i>
                    <span>${reservasHoy.length > 0 
                        ? `Ocupado: ${reservasHoy.map(r => r.horaInicio + '-' + r.horaFin).join(', ')}` 
                        : 'Disponible todo el día'}</span>
                </div>
            </div>

            <div class="espacio-actions">
                <button class="btn-card-reserve btn-edif1-card" onclick="openReservaParaEspacio(${e.idEspacio})">
                    <i class="ph-bold ph-calendar-plus"></i> Reservar Salón ${e.nombreEspacio}
                </button>
                <button class="btn-icon" style="width:36px;height:36px;" title="Editar Salón" onclick="abrirEditarEspacio(${e.idEspacio})">
                    <i class="ph-bold ph-pencil-simple"></i>
                </button>
            </div>
        `;
        grid.appendChild(card);
    });
}

// ====================================================================
// 4. VISTA DEDICADA: EDIFICIO 2 (PROFESIONAL)
// ====================================================================
function renderEdificio2View() {
    const grid = document.getElementById('gridEdificio2Dedicada');
    if (!grid) return;
    grid.innerHTML = '';

    const salones = state.espacios
        .filter(isEdificio2)
        .sort((a, b) => a.nombreEspacio.localeCompare(b.nombreEspacio, undefined, { numeric: true }));

    const dateVal = document.getElementById('edificio2DateSelector') ? document.getElementById('edificio2DateSelector').value : new Date().toISOString().split('T')[0];

    if (salones.length === 0) {
        grid.innerHTML = `<div style="grid-column: 1/-1;text-align:center;padding:32px;color:var(--text-muted);">No hay salones registrados en el Edificio 2 (Profesional).</div>`;
        return;
    }

    salones.forEach(e => {
        const reservasHoy = state.reservas.filter(r => 
            r.idEspacio === e.idEspacio && 
            r.fechaReserva === dateVal && 
            r.estadoReserva === 'Activa'
        );

        const card = document.createElement('div');
        card.className = 'espacio-card is-edif2';
        card.innerHTML = `
            <div class="espacio-header">
                <div>
                    <h3 class="espacio-name">Salón ${e.nombreEspacio}</h3>
                    <span class="badge-type edificio2">🏛️ Edificio 2 (Profesional)</span>
                </div>
                <span class="badge-status ${reservasHoy.length === 0 ? 'activa' : 'cancelada'}">
                    ${reservasHoy.length === 0 ? 'Libre' : `${reservasHoy.length} en uso`}
                </span>
            </div>

            <div class="espacio-details">
                <div class="detail-row">
                    <i class="ph-bold ph-users"></i>
                    <span>Capacidad: <strong>${e.capacidad} estudiantes</strong></span>
                </div>
                <div class="detail-row">
                    <i class="ph-bold ph-clock"></i>
                    <span>${reservasHoy.length > 0 
                        ? `Ocupado: ${reservasHoy.map(r => r.horaInicio + '-' + r.horaFin).join(', ')}` 
                        : 'Disponible todo el día'}</span>
                </div>
            </div>

            <div class="espacio-actions">
                <button class="btn-card-reserve btn-edif2-card" onclick="openReservaParaEspacio(${e.idEspacio})">
                    <i class="ph-bold ph-calendar-plus"></i> Reservar Salón ${e.nombreEspacio}
                </button>
                <button class="btn-icon" style="width:36px;height:36px;" title="Editar Salón" onclick="abrirEditarEspacio(${e.idEspacio})">
                    <i class="ph-bold ph-pencil-simple"></i>
                </button>
            </div>
        `;
        grid.appendChild(card);
    });
}

// ====================================================================
// 5. VISTA DEDICADA: ALDEA
// ====================================================================
function renderAldeaView() {
    const grid = document.getElementById('gridAldeaDedicada');
    if (!grid) return;
    grid.innerHTML = '';

    const aldeaEspacios = state.espacios
        .filter(isAldea)
        .sort((a, b) => a.nombreEspacio.localeCompare(b.nombreEspacio, undefined, { numeric: true }));

    const dateVal = document.getElementById('aldeaDateSelector') ? document.getElementById('aldeaDateSelector').value : new Date().toISOString().split('T')[0];

    if (aldeaEspacios.length === 0) {
        grid.innerHTML = `<div style="grid-column: 1/-1;text-align:center;padding:32px;color:var(--text-muted);">No hay salones registrados en la Aldea.</div>`;
        return;
    }

    aldeaEspacios.forEach(e => {
        const reservasHoy = state.reservas.filter(r => 
            r.idEspacio === e.idEspacio && 
            r.fechaReserva === dateVal && 
            r.estadoReserva === 'Activa'
        );

        const card = document.createElement('div');
        card.className = 'espacio-card is-aldea';
        card.innerHTML = `
            <div class="espacio-header">
                <div>
                    <h3 class="espacio-name">Salón ${e.nombreEspacio}</h3>
                    <span class="badge-type aldea">🌿 Aldea</span>
                </div>
                <span class="badge-status ${reservasHoy.length === 0 ? 'activa' : 'cancelada'}">
                    ${reservasHoy.length === 0 ? 'Libre' : `${reservasHoy.length} en uso`}
                </span>
            </div>

            <div class="espacio-details">
                <div class="detail-row">
                    <i class="ph-bold ph-users"></i>
                    <span>Capacidad: <strong>${e.capacidad} estudiantes</strong></span>
                </div>
                <div class="detail-row">
                    <i class="ph-bold ph-clock"></i>
                    <span>${reservasHoy.length > 0 
                        ? `Ocupado: ${reservasHoy.map(r => r.horaInicio + '-' + r.horaFin).join(', ')}` 
                        : 'Disponible todo el día'}</span>
                </div>
            </div>

            <div class="espacio-actions">
                <button class="btn-card-reserve btn-aldea-card" onclick="openReservaParaEspacio(${e.idEspacio})">
                    <i class="ph-bold ph-calendar-plus"></i> Reservar Salón ${e.nombreEspacio}
                </button>
                <button class="btn-icon" style="width:36px;height:36px;" title="Editar Salón" onclick="abrirEditarEspacio(${e.idEspacio})">
                    <i class="ph-bold ph-pencil-simple"></i>
                </button>
            </div>
        `;
        grid.appendChild(card);
    });
}

// ====================================================================
// 6. CATÁLOGO GENERAL DE ESPACIOS
// ====================================================================
function renderEspaciosGrid() {
    const grid = document.getElementById('gridEspacios');
    if (!grid) return;
    grid.innerHTML = '';

    let filtrados = state.espacios;
    if (state.espacioFilter !== 'all') {
        if (state.espacioFilter === 'Edificio1') {
            filtrados = filtrados.filter(isEdificio1);
        } else if (state.espacioFilter === 'Edificio2') {
            filtrados = filtrados.filter(isEdificio2);
        } else if (state.espacioFilter === 'Aldea') {
            filtrados = filtrados.filter(isAldea);
        } else if (state.espacioFilter === 'Mezanine') {
            filtrados = filtrados.filter(isMezanine);
        } else {
            filtrados = filtrados.filter(e => e.tipoEspacio === state.espacioFilter && !isMezanine(e));
        }
    }
    if (state.searchQuery) {
        const q = state.searchQuery.toLowerCase();
        filtrados = filtrados.filter(e => 
            e.nombreEspacio.toLowerCase().includes(q) ||
            e.tipoEspacio.toLowerCase().includes(q)
        );
    }

    if (filtrados.length === 0) {
        grid.innerHTML = `<div style="grid-column: 1/-1;text-align:center;padding:48px;color:var(--text-muted)">No hay espacios para la categoría seleccionada.</div>`;
        return;
    }

    filtrados.forEach(e => {
        const card = document.createElement('div');
        let cardTypeClass = 'is-salon';
        let badgeLabel = e.tipoEspacio;
        let badgeClass = (e.tipoEspacio || '').toLowerCase();
        let subtitle = 'Espacio universitario disponible';

        if (isMezanine(e)) {
            cardTypeClass = 'is-mezanine';
            badgeLabel = '📖 Mezanine';
            badgeClass = 'mezanine';
            subtitle = 'Cubículo institucional';
        } else if (isEdificio1(e)) {
            cardTypeClass = 'is-edif1';
            badgeLabel = '🏢 Edificio 1 (Prepa)';
            badgeClass = 'edificio1';
            subtitle = 'Edificio de Preparatoria';
        } else if (isEdificio2(e)) {
            cardTypeClass = 'is-edif2';
            badgeLabel = '🏛️ Edificio 2 (Prof)';
            badgeClass = 'edificio2';
            subtitle = 'Edificio 2 de Profesional';
        } else if (isAldea(e)) {
            cardTypeClass = 'is-aldea';
            badgeLabel = '🌿 Aldea';
            badgeClass = 'aldea';
            subtitle = 'Aulas de La Aldea';
        } else if (e.tipoEspacio === 'Laboratorio') {
            cardTypeClass = 'is-lab';
            badgeLabel = '🧪 Laboratorio';
            badgeClass = 'laboratorio';
            subtitle = 'Laboratorio Especializado';
        } else if (e.tipoEspacio === 'Cancha') {
            cardTypeClass = 'is-cancha';
            badgeLabel = '⚽ Cancha';
            badgeClass = 'cancha';
            subtitle = 'Área Deportiva';
        } else if (e.tipoEspacio === 'Otro') {
            cardTypeClass = 'is-otro';
            badgeLabel = '🌅 Otros';
            badgeClass = 'otro';
            subtitle = 'Espacio de Uso Común';
        }

        card.className = `espacio-card ${cardTypeClass}`;
        card.innerHTML = `
            <div class="espacio-header">
                <div>
                    <h3 class="espacio-name">${e.nombreEspacio}</h3>
                    <span class="badge-type ${badgeClass}">${badgeLabel}</span>
                </div>
                <span class="badge-status ${e.activo ? 'activa' : 'cancelada'}">${e.activo ? 'Disponible' : 'Inactivo'}</span>
            </div>

            <div class="espacio-details">
                <div class="detail-row">
                    <i class="ph-bold ph-users"></i>
                    <span>Capacidad: <strong>${e.capacidad} personas</strong></span>
                </div>
                <div class="detail-row">
                    <i class="ph-bold ph-info"></i>
                    <span>${subtitle}</span>
                </div>
                ${isMezanine(e) ? `
                    <div style="margin-top:8px;background:#fef2f2;border:1px solid #fecaca;padding:6px 10px;border-radius:6px;font-size:0.75rem;color:#991b1b;display:flex;align-items:center;gap:6px;">
                        <i class="ph-bold ph-lock" style="font-size:0.95rem;color:#dc2626;"></i>
                        <span><strong>Ocupado Lun-Vie 07:00 a 15:00</strong><br><span style="color:#15803d;font-weight:600;">Disponible a partir de las 3:00 PM</span></span>
                    </div>
                ` : ''}
            </div>

            <div class="espacio-actions">
                <button class="btn-card-reserve ${isEdificio1(e) ? 'btn-edif1-card' : isEdificio2(e) ? 'btn-edif2-card' : isAldea(e) ? 'btn-aldea-card' : ''}" onclick="openReservaParaEspacio(${e.idEspacio})">
                    <i class="ph-bold ph-calendar-plus"></i> Agendar Aquí
                </button>
                <button class="btn-icon" style="width:36px;height:36px;" title="Editar Espacio" onclick="abrirEditarEspacio(${e.idEspacio})">
                    <i class="ph-bold ph-pencil-simple"></i>
                </button>
            </div>
        `;
        grid.appendChild(card);
    });
}

// ====================================================================
// 7. USUARIOS
// ====================================================================
function renderUsuarios() {
    const tbody = document.getElementById('tbodyUsuarios');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (state.usuarios.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--text-muted);padding:24px;">No hay usuarios registrados.</td></tr>`;
        return;
    }

    state.usuarios.forEach(u => {
        const tr = document.createElement('tr');
        const puedeAgendar = u.rol !== 'Profesor';
        const badgeRoleClass = u.rol === 'Administrador' ? 'edificio1' : u.rol === 'Responsable' ? 'edificio2' : u.rol === 'Colaborador' ? 'aldea' : 'otro';

        tr.innerHTML = `
            <td>#${u.idUsuario}</td>
            <td><strong>${escapeHtml(u.nombreCompleto)}</strong></td>
            <td><a href="mailto:${u.correo}" style="color:var(--tecmilenio-green);text-decoration:none;font-weight:500;">${escapeHtml(u.correo)}</a></td>
            <td><span class="badge-type ${badgeRoleClass}">${escapeHtml(u.rol)}</span></td>
            <td>
                ${puedeAgendar ? `
                    <button class="btn-secondary" style="padding:6px 12px;font-size:0.8rem;" onclick="openReservaConUsuario(${u.idUsuario})">
                        <i class="ph-bold ph-calendar-plus"></i> Agendar como ${escapeHtml(u.nombreCompleto.split(' ')[0])}
                    </button>
                ` : `
                    <span style="font-size:0.75rem;color:var(--text-muted);" title="Los profesores requieren un Colaborador o Responsable para agendar">
                        <i class="ph-bold ph-lock-key"></i> Requiere Colaborador
                    </span>
                `}
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function populateSelects() {
    // 1. Select de Responsable / Colaborador (SOLO autorizados para agendar)
    const uSelect = document.getElementById('reservaUsuario');
    if (uSelect) {
        const prevVal = uSelect.value;
        uSelect.innerHTML = '<option value="">Selecciona al Responsable / Colaborador...</option>';
        
        const autorizados = state.usuarios.filter(u => u.rol !== 'Profesor');
        autorizados.sort((a, b) => a.nombreCompleto.localeCompare(b.nombreCompleto));

        autorizados.forEach(u => {
            const opt = document.createElement('option');
            opt.value = u.idUsuario;
            opt.textContent = `${u.nombreCompleto} (${u.rol})`;
            uSelect.appendChild(opt);
        });
        if (prevVal) uSelect.value = prevVal;
    }

    // 2. Select de Profesor Asignado a la clase (opcional pero muy útil)
    const profSelect = document.getElementById('reservaProfesor');
    if (profSelect) {
        const prevProfVal = profSelect.value;
        profSelect.innerHTML = '<option value="">-- Sin profesor / Asignar después --</option>';

        const profesores = state.usuarios.filter(u => u.rol === 'Profesor');
        profesores.sort((a, b) => a.nombreCompleto.localeCompare(b.nombreCompleto));

        profesores.forEach(p => {
            const opt = document.createElement('option');
            opt.value = p.nombreCompleto;
            opt.textContent = `${p.nombreCompleto} (${p.correo})`;
            profSelect.appendChild(opt);
        });
        if (prevProfVal) profSelect.value = prevProfVal;
    }

    // 3. Select de espacios agrupados por edificio institucional
    const eSelect = document.getElementById('reservaEspacio');
    if (eSelect) {
        const prevVal = eSelect.value;
        eSelect.innerHTML = '<option value="">Selecciona un espacio...</option>';

        const grupos = [
            {
                label: '🏢 Edificio 1 (Preparatoria: 1102 - 1310)',
                filter: isEdificio1
            },
            {
                label: '🏛️ Edificio 2 (Profesional: 2201 - 2405)',
                filter: isEdificio2
            },
            {
                label: '🌿 Aldea (2101 - 2113)',
                filter: isAldea
            },
            {
                label: '🧪 Laboratorios Especializados',
                filter: e => e.tipoEspacio === 'Laboratorio'
            },
            {
                label: '⚽ Canchas Deportivas',
                filter: e => e.tipoEspacio === 'Cancha'
            },
            {
                label: '📖 Cubículos Mezanine (Ocupado Lun-Vie 7-15h | Libre > 3 PM)',
                filter: isMezanine
            },
            {
                label: '🌅 Otros Espacios',
                filter: e => e.tipoEspacio === 'Otro' && !isMezanine(e)
            }
        ];

        grupos.forEach(g => {
            const spaces = state.espacios.filter(e => g.filter(e) && e.activo);
            if (spaces.length > 0) {
                const optgroup = document.createElement('optgroup');
                optgroup.label = g.label;
                spaces.sort((a, b) => a.nombreEspacio.localeCompare(b.nombreEspacio, undefined, { numeric: true }));
                spaces.forEach(e => {
                    const opt = document.createElement('option');
                    opt.value = e.idEspacio;
                    opt.textContent = `Salón ${e.nombreEspacio} (Capacidad: ${e.capacidad})`;
                    optgroup.appendChild(opt);
                });
                eSelect.appendChild(optgroup);
            }
        });

        if (prevVal) eSelect.value = prevVal;
    }
}

// ====================================================================
// GESTIÓN DE MODALES Y ACCIONES
// ====================================================================
function setupModals() {
    // Botón abrir modal reserva
    const btnRes = document.getElementById('btnOpenModalReserva');
    if (btnRes) {
        btnRes.onclick = () => abrirModalNuevaReserva();
    }

    // Botón abrir modal nuevo espacio
    const btnEsp = document.getElementById('btnOpenModalEspacio');
    if (btnEsp) {
        btnEsp.onclick = () => {
            document.getElementById('modalEspacioTitle').textContent = 'Nuevo Espacio Universitario';
            document.getElementById('formEspacio').reset();
            document.getElementById('espacioEditId').value = '';
            openModal('modalEspacio');
        };
    }

    // Botón abrir modal nuevo usuario
    const btnUsu = document.getElementById('btnOpenModalUsuario');
    if (btnUsu) {
        btnUsu.onclick = () => {
            document.getElementById('formUsuario').reset();
            openModal('modalUsuario');
        };
    }

    // Envío del formulario de reservas
    const formRes = document.getElementById('formReserva');
    if (formRes) {
        formRes.onsubmit = async (e) => {
            e.preventDefault();
            await handleReservaSubmit();
        };
    }

    // Envío del formulario de espacio
    const formEsp = document.getElementById('formEspacio');
    if (formEsp) {
        formEsp.onsubmit = async (e) => {
            e.preventDefault();
            await handleEspacioSubmit();
        };
    }

    // Envío del formulario de usuario
    const formUsu = document.getElementById('formUsuario');
    if (formUsu) {
        formUsu.onsubmit = async (e) => {
            e.preventDefault();
            await handleUsuarioSubmit();
        };
    }

    // Al cambiar espacio en el select de reservas, ajustar capacidad sugerida
    const selectEsp = document.getElementById('reservaEspacio');
    if (selectEsp) {
        selectEsp.onchange = (e) => {
            const id = parseInt(e.target.value);
            const esp = state.espacios.find(x => x.idEspacio === id);
            const hint = document.getElementById('espacioHint');
            const capInput = document.getElementById('reservaCapacidad');
            if (esp) {
                if (isMezanine(esp)) {
                    if (hint) hint.innerHTML = `<span style="color:#b91c1c;font-weight:600;"><i class="ph-bold ph-warning"></i> Cubículo Mezanine: Ocupado de lunes a viernes de 7:00 AM a 3:00 PM. Se puede agendar después de las 3:00 PM.</span>`;
                } else {
                    if (hint) hint.textContent = `${getEspacioEdificioLabel(esp)} - Capacidad sugerida: ${esp.capacidad} personas`;
                }
                if (capInput && !capInput.value) capInput.value = esp.capacidad;
            } else {
                if (hint) hint.textContent = '';
            }
            checkConflictNow();
        };
    }
}

function openModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.classList.add('active');
        document.body.style.overflow = 'hidden';
    }
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.classList.remove('active');
        document.body.style.overflow = '';
    }
}

function abrirModalNuevaReserva() {
    document.getElementById('modalReservaTitle').textContent = 'Agendar Nuevo Espacio';
    document.getElementById('formReserva').reset();
    document.getElementById('reservaEditId').value = '';
    
    const chkMed = document.getElementById('reservaServicioMedico');
    if (chkMed) chkMed.checked = false;
    const chkTI = document.getElementById('reservaServicioTI');
    if (chkTI) chkTI.checked = false;

    resetLayoutUploadUI();

    const rowEstado = document.getElementById('rowReservaEstado');
    if (rowEstado) rowEstado.style.display = 'none';

    // Fecha actual por defecto
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('reservaFecha').value = today;

    // Horario por defecto (ej. 10:00 a 11:30)
    document.getElementById('reservaHoraInicio').value = '10:00';
    document.getElementById('reservaHoraFin').value = '11:30';

    openModal('modalReserva');
    checkConflictNow();
}

function abrirEditarReserva(idReserva) {
    const r = state.reservas.find(x => x.idReserva === idReserva);
    if (!r) return;

    document.getElementById('modalReservaTitle').textContent = `Editar Reserva #${r.idReserva}`;
    document.getElementById('reservaEditId').value = r.idReserva;
    document.getElementById('reservaUsuario').value = r.idUsuario;
    document.getElementById('reservaEspacio').value = r.idEspacio;
    document.getElementById('reservaCapacidad').value = r.capacidadRequerida || 30;
    document.getElementById('reservaFecha').value = r.fechaReserva;
    document.getElementById('reservaHoraInicio').value = r.horaInicio;
    document.getElementById('reservaHoraFin').value = r.horaFin;
    document.getElementById('reservaMotivo').value = r.motivoEvento;
    document.getElementById('reservaObs').value = r.observaciones || '';

    const chkMed = document.getElementById('reservaServicioMedico');
    if (chkMed) chkMed.checked = !!r.servicioMedico;
    const chkTI = document.getElementById('reservaServicioTI');
    if (chkTI) chkTI.checked = !!r.servicioTI;

    // Cargar archivo de layout si existe
    cargarLayoutEnEdicion(r);

    const rowEstado = document.getElementById('rowReservaEstado');
    if (rowEstado) {
        rowEstado.style.display = 'flex';
        document.getElementById('reservaEstado').value = r.estadoReserva;
    }

    openModal('modalReserva');
    checkConflictNow();
}

function openReservaEnHorario(idEspacio, fecha, hInicio, hFin) {
    const esp = state.espacios.find(e => e.idEspacio === idEspacio);
    if (esp && isMezanine(esp) && isWeekdayDate(fecha)) {
        const sMin = toMinutes(hInicio);
        const eMin = toMinutes(hFin);
        if (sMin < 15 * 60 && eMin > 7 * 60) {
            showToast('Los cubículos de Mezanine están ocupados de lunes a viernes de 7:00 AM a 3:00 PM. Solo se puede agendar después de las 3:00 PM.', 'warning');
            return;
        }
    }
    abrirModalNuevaReserva();
    const select = document.getElementById('reservaEspacio');
    select.value = idEspacio;
    select.dispatchEvent(new Event('change'));

    document.getElementById('reservaFecha').value = fecha;
    document.getElementById('reservaHoraInicio').value = hInicio;
    document.getElementById('reservaHoraFin').value = hFin;
    checkConflictNow();
}

function openReservaParaEspacio(idEspacio) {
    abrirModalNuevaReserva();
    const select = document.getElementById('reservaEspacio');
    if (select) {
        select.value = idEspacio;
        select.dispatchEvent(new Event('change'));
    }
}

function openReservaConUsuario(idUsuario) {
    abrirModalNuevaReserva();
    const uSelect = document.getElementById('reservaUsuario');
    if (uSelect) uSelect.value = idUsuario;
}

function openModalReservaConEdificio(edificio) {
    abrirModalNuevaReserva();
    let firstMatching;
    if (edificio === 'Edificio1') {
        firstMatching = state.espacios.find(e => isEdificio1(e) && e.activo);
    } else if (edificio === 'Edificio2') {
        firstMatching = state.espacios.find(e => isEdificio2(e) && e.activo);
    } else if (edificio === 'Aldea') {
        firstMatching = state.espacios.find(e => isAldea(e) && e.activo);
    }
    if (firstMatching) {
        openReservaParaEspacio(firstMatching.idEspacio);
    }
}

function abrirEditarEspacio(idEspacio) {
    const e = state.espacios.find(x => x.idEspacio === idEspacio);
    if (!e) return;

    document.getElementById('modalEspacioTitle').textContent = `Editar Espacio: ${e.nombreEspacio}`;
    document.getElementById('espacioEditId').value = e.idEspacio;
    document.getElementById('espacioNombre').value = e.nombreEspacio;
    document.getElementById('espacioTipo').value = isEdificio1(e) ? 'Edificio1' : isEdificio2(e) ? 'Edificio2' : isAldea(e) ? 'Aldea' : e.tipoEspacio;
    document.getElementById('espacioCapacidad').value = e.capacidad;
    document.getElementById('espacioActivo').checked = e.activo;

    openModal('modalEspacio');
}

// ====================================================================
// VALIDACIÓN DE TRASLAPE / CONFLICTO EN TIEMPO REAL
// ====================================================================
function setupRealtimeConflictCheck() {
    ['reservaEspacio', 'reservaFecha', 'reservaHoraInicio', 'reservaHoraFin'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.addEventListener('input', checkConflictNow);
            el.addEventListener('change', checkConflictNow);
        }
    });
}

function checkConflictNow() {
    const selectEsp = document.getElementById('reservaEspacio');
    const inputFecha = document.getElementById('reservaFecha');
    const inputHInicio = document.getElementById('reservaHoraInicio');
    const inputHFin = document.getElementById('reservaHoraFin');
    const alertBox = document.getElementById('conflictAlertBox');
    const submitBtn = document.getElementById('btnSubmitReserva');

    if (!selectEsp || !inputFecha || !inputHInicio || !inputHFin || !alertBox || !submitBtn) return;

    const idE = parseInt(selectEsp.value);
    const fecha = inputFecha.value;
    const hInicio = inputHInicio.value;
    const hFin = inputHFin.value;

    if (!idE || !fecha || !hInicio || !hFin) {
        alertBox.style.display = 'none';
        submitBtn.disabled = false;
        return;
    }

    if (hInicio >= hFin) {
        alertBox.style.display = 'flex';
        document.getElementById('conflictTitle').textContent = 'Horario inválido';
        document.getElementById('conflictDesc').textContent = 'La hora de inicio debe ser anterior a la hora de fin.';
        submitBtn.disabled = true;
        return;
    }

    const editId = parseInt(document.getElementById('reservaEditId').value) || 0;
    const startMin = toMinutes(hInicio);
    const endMin = toMinutes(hFin);

    // Restricción institucional: Mezanine ocupado de Lunes a Viernes de 7:00 a 15:00 (3:00 PM)
    const espSeleccionado = state.espacios.find(x => x.idEspacio === idE);
    if (espSeleccionado && isMezanine(espSeleccionado) && isWeekdayDate(fecha)) {
        if (startMin < 15 * 60 && endMin > 7 * 60) {
            alertBox.style.display = 'flex';
            document.getElementById('conflictTitle').textContent = `¡Cubículo de Mezanine Ocupado!`;
            document.getElementById('conflictDesc').textContent = `Todos los cubículos de Mezanine están ocupados de lunes a viernes de 7:00 AM a 3:00 PM (15:00). Después de las 3:00 PM se puede agendar.`;
            submitBtn.disabled = true;
            return;
        }
    }

    const conflicto = state.reservas.find(r => {
        if (editId && r.idReserva === editId) return false;
        if (r.idEspacio !== idE) return false;
        if (r.fechaReserva !== fecha) return false;
        if (r.estadoReserva !== 'Activa') return false;

        const rStart = toMinutes(r.horaInicio);
        const rEnd = toMinutes(r.horaFin);
        return startMin < rEnd && endMin > rStart;
    });

    if (conflicto) {
        alertBox.style.display = 'flex';
        document.getElementById('conflictTitle').textContent = `¡Conflicto con ${conflicto.nombreEspacio}!`;
        document.getElementById('conflictDesc').textContent = `Ya está agendado de ${conflicto.horaInicio} a ${conflicto.horaFin} por ${conflicto.nombreUsuario || 'otro profesor'} para: "${conflicto.motivoEvento}".`;
        submitBtn.disabled = true;
    } else {
        alertBox.style.display = 'none';
        submitBtn.disabled = false;
    }
}

function toMinutes(time) {
    if (!time) return 0;
    const parts = time.split(':');
    return parseInt(parts[0]) * 60 + parseInt(parts[1]);
}

// ====================================================================
// ACCIONES DE FORMULARIO: RESERVA, ESPACIO, USUARIO
// ====================================================================
async function handleReservaSubmit() {
    const editId = document.getElementById('reservaEditId').value;
    const idUsuario = parseInt(document.getElementById('reservaUsuario').value);
    const idEspacio = parseInt(document.getElementById('reservaEspacio').value);

    if (!idUsuario) {
        showToast('Debes seleccionar al Responsable o Colaborador autorizado para registrar la reserva.', 'warning');
        return;
    }

    // Validar regla de autorización: Los profesores NO pueden agendar por su cuenta
    const usuarioAutorizado = state.usuarios.find(u => u.idUsuario === idUsuario);
    if (usuarioAutorizado && usuarioAutorizado.rol === 'Profesor') {
        showToast('Los profesores NO tienen autorización para agendar por su cuenta. Selecciona a un Responsable o Colaborador.', 'error');
        return;
    }

    // Validar regla institucional de Mezanine (Lunes a Viernes 7:00 a 15:00 ocupado)
    const espSeleccionado = state.espacios.find(e => e.idEspacio === idEspacio);
    const fechaReservaVal = document.getElementById('reservaFecha').value;
    const hInicioVal = document.getElementById('reservaHoraInicio').value;
    const hFinVal = document.getElementById('reservaHoraFin').value;
    if (espSeleccionado && isMezanine(espSeleccionado) && isWeekdayDate(fechaReservaVal)) {
        const sMin = toMinutes(hInicioVal);
        const eMin = toMinutes(hFinVal);
        if (sMin < 15 * 60 && eMin > 7 * 60) {
            showToast('Los cubículos de Mezanine están ocupados de lunes a viernes de 7:00 AM a 3:00 PM. Solo se puede agendar a partir de las 3:00 PM.', 'error');
            return;
        }
    }

    // Profesor asignado a la clase (si se especificó en el selector)
    const selectProfesor = document.getElementById('reservaProfesor');
    const profesorAsignado = selectProfesor ? selectProfesor.value : '';

    let obs = document.getElementById('reservaObs').value.trim();
    if (profesorAsignado && !obs.includes(profesorAsignado)) {
        obs = obs ? `${obs} | Profesor: ${profesorAsignado}` : `Profesor: ${profesorAsignado}`;
    }

    const chkMed = document.getElementById('reservaServicioMedico');
    const servicioMedico = chkMed ? chkMed.checked : false;
    const chkTI = document.getElementById('reservaServicioTI');
    const servicioTI = chkTI ? chkTI.checked : false;

    // Subida y procesamiento de archivo de layout (máx 10 MB)
    let finalLayoutUrl = document.getElementById('reservaLayoutUrl') ? document.getElementById('reservaLayoutUrl').value : '';
    let finalLayoutNombre = document.getElementById('reservaLayoutNombre') ? document.getElementById('reservaLayoutNombre').value : '';

    if (pendingLayoutFile && pendingLayoutBase64) {
        if (state.backendConnected) {
            try {
                const upRes = await fetch(`${API_BASE}/upload`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        fileName: pendingLayoutFile.name,
                        fileData: pendingLayoutBase64
                    })
                });
                if (upRes.ok) {
                    const upData = await upRes.json();
                    finalLayoutUrl = upData.url;
                    finalLayoutNombre = upData.fileName || pendingLayoutFile.name;
                } else {
                    const upErr = await upRes.json();
                    throw new Error(upErr.error || 'Error al procesar el archivo de layout');
                }
            } catch (upEx) {
                showToast(upEx.message, 'error');
                return;
            }
        } else {
            finalLayoutUrl = pendingLayoutBase64;
            finalLayoutNombre = pendingLayoutFile.name;
        }
    }

    const payload = {
        idUsuario: idUsuario,
        idEspacio: idEspacio,
        fechaReserva: document.getElementById('reservaFecha').value,
        horaInicio: document.getElementById('reservaHoraInicio').value,
        horaFin: document.getElementById('reservaHoraFin').value,
        motivoEvento: document.getElementById('reservaMotivo').value.trim(),
        observaciones: obs,
        servicioMedico: servicioMedico,
        servicioTI: servicioTI,
        archivoLayout: finalLayoutUrl,
        archivoLayoutNombre: finalLayoutNombre
    };

    if (editId) {
        payload.estadoReserva = document.getElementById('reservaEstado').value;
    }

    try {
        let savedId = null;
        if (state.backendConnected) {
            const url = editId ? `${API_BASE}/reservas/${editId}` : `${API_BASE}/reservas`;
            const method = editId ? 'PUT' : 'POST';

            const res = await fetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                const err = await res.json();
                throw new Error(err.error || 'No se pudo guardar la reserva');
            }

            const data = await res.json();
            savedId = data.idReserva || (editId ? parseInt(editId) : null);
            showToast(editId ? '¡Reserva actualizada correctamente!' : '¡Reserva confirmada con éxito!', 'success');
        } else {
            // Modo local fallback
            if (editId) {
                savedId = parseInt(editId);
                const idx = state.reservas.findIndex(r => r.idReserva === savedId);
                if (idx !== -1) {
                    state.reservas[idx] = { ...state.reservas[idx], ...payload };
                }
            } else {
                const newId = state.reservas.length ? Math.max(...state.reservas.map(r => r.idReserva)) + 1 : 1;
                savedId = newId;
                const esp = state.espacios.find(e => e.idEspacio === payload.idEspacio);
                const usu = state.usuarios.find(u => u.idUsuario === payload.idUsuario);
                state.reservas.unshift({
                    idReserva: newId,
                    ...payload,
                    estadoReserva: 'Activa',
                    nombreEspacio: esp ? esp.nombreEspacio : '',
                    tipoEspacio: esp ? esp.tipoEspacio : '',
                    nombreUsuario: usu ? usu.nombreCompleto : ''
                });
            }
            showToast('Reserva guardada (Modo local)', 'success');
        }

        closeModal('modalReserva');
        await loadAllData();

        if (savedId) {
            mostrarModalExitoReserva(savedId);
        }
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function handleEspacioSubmit() {
    const editId = document.getElementById('espacioEditId').value;
    const payload = {
        nombreEspacio: document.getElementById('espacioNombre').value.trim(),
        tipoEspacio: document.getElementById('espacioTipo').value,
        capacidad: parseInt(document.getElementById('espacioCapacidad').value) || 30,
        activo: document.getElementById('espacioActivo').checked
    };

    try {
        if (state.backendConnected) {
            const url = editId ? `${API_BASE}/espacios/${editId}` : `${API_BASE}/espacios`;
            const method = editId ? 'PUT' : 'POST';

            const res = await fetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                const err = await res.json();
                throw new Error(err.error || 'Error al guardar el espacio');
            }

            showToast(editId ? '¡Espacio actualizado!' : '¡Nuevo espacio registrado!', 'success');
        } else {
            if (editId) {
                const idx = state.espacios.findIndex(e => e.idEspacio === parseInt(editId));
                if (idx !== -1) state.espacios[idx] = { ...state.espacios[idx], ...payload };
            } else {
                const id = state.espacios.length ? Math.max(...state.espacios.map(e => e.idEspacio)) + 1 : 1;
                state.espacios.push({ idEspacio: id, ...payload });
            }
            showToast('Espacio guardado (Modo local)', 'success');
        }

        closeModal('modalEspacio');
        await loadAllData();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function handleUsuarioSubmit() {
    const payload = {
        nombreCompleto: document.getElementById('usuarioNombre').value.trim(),
        correo: document.getElementById('usuarioCorreo').value.trim(),
        rol: document.getElementById('usuarioRol').value
    };

    try {
        if (state.backendConnected) {
            const res = await fetch(`${API_BASE}/usuarios`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!res.ok) {
                const err = await res.json();
                throw new Error(err.error || 'Error al registrar usuario');
            }
            showToast('¡Usuario registrado con éxito!', 'success');
        } else {
            const id = state.usuarios.length ? Math.max(...state.usuarios.map(u => u.idUsuario)) + 1 : 1;
            state.usuarios.push({ idUsuario: id, ...payload });
            showToast('Usuario guardado en memoria', 'success');
        }

        closeModal('modalUsuario');
        await loadAllData();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function cambiarEstadoReserva(idReserva, nuevoEstado) {
    try {
        if (state.backendConnected) {
            const res = await fetch(`${API_BASE}/reservas/${idReserva}/estado`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ estadoReserva: nuevoEstado })
            });
            if (!res.ok) throw new Error('No se pudo actualizar el estado');
            showToast(`Reserva marcada como ${nuevoEstado}`, 'success');
        } else {
            const r = state.reservas.find(x => x.idReserva === idReserva);
            if (r) r.estadoReserva = nuevoEstado;
            showToast(`Reserva marcada como ${nuevoEstado}`, 'success');
        }
        await loadAllData();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function eliminarReserva(idReserva) {
    if (!confirm('¿Deseas eliminar este registro de reserva?')) return;

    try {
        if (state.backendConnected) {
            const res = await fetch(`${API_BASE}/reservas/${idReserva}`, { method: 'DELETE' });
            if (!res.ok) throw new Error('No se pudo eliminar la reserva');
            showToast('Reserva eliminada del sistema', 'success');
        } else {
            state.reservas = state.reservas.filter(x => x.idReserva !== idReserva);
            showToast('Reserva eliminada localmente', 'success');
        }
        await loadAllData();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ====================================================================
// NAVEGACIÓN Y FILTROS
// ====================================================================
function setupNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.onclick = () => {
            const tabId = item.dataset.tab;
            switchTab(tabId);
        };
    });

    const btnRef = document.getElementById('btnRefresh');
    if (btnRef) {
        btnRef.onclick = () => {
            loadAllData();
            showToast('Datos actualizados', 'info');
        };
    }
}

function switchTab(tabId) {
    state.activeTab = tabId;
    document.querySelectorAll('.nav-item').forEach(i => i.classList.remove('active'));
    document.querySelectorAll('.tab-view').forEach(v => v.classList.remove('active'));

    const activeBtn = document.querySelector(`.nav-item[data-tab="${tabId}"]`);
    if (activeBtn) activeBtn.classList.add('active');

    const activeView = document.getElementById(`view-${tabId}`);
    if (activeView) activeView.classList.add('active');

    // Re-render vista activa para refrescar fechas
    if (tabId === 'edificio1') renderEdificio1View();
    if (tabId === 'edificio2') renderEdificio2View();
    if (tabId === 'aldea') renderAldeaView();
    if (tabId === 'agenda') renderAgenda();
    if (tabId === 'espacios') renderEspaciosGrid();
}

function setupSearchAndFilters() {
    // Búsqueda global en el header
    const searchInput = document.getElementById('globalSearchInput');
    if (searchInput) {
        searchInput.oninput = (e) => {
            state.searchQuery = e.target.value.trim();
            renderAgenda();
            renderEspaciosGrid();
        };
    }

    // Filtros de agenda
    ['filterFecha', 'filterTipoEspacio', 'filterEstado'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.onchange = renderAgenda;
    });

    const btnClear = document.getElementById('btnClearFilters');
    if (btnClear) {
        btnClear.onclick = () => {
            if (document.getElementById('filterFecha')) document.getElementById('filterFecha').value = '';
            if (document.getElementById('filterTipoEspacio')) document.getElementById('filterTipoEspacio').value = '';
            if (document.getElementById('filterEstado')) document.getElementById('filterEstado').value = '';
            renderAgenda();
        };
    }

    // Pills de catálogo de espacios
    document.querySelectorAll('.pill-btn').forEach(btn => {
        btn.onclick = () => {
            document.querySelectorAll('.pill-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            state.espacioFilter = btn.dataset.espacioFilter;
            renderEspaciosGrid();
        };
    });

    // Exportar a Excel (CSV)
    const btnExp = document.getElementById('btnExportarCsv');
    if (btnExp) btnExp.onclick = exportarReservasCsv;

    // Navegación de días en Timeline
    const btnPrev = document.getElementById('btnPrevDay');
    if (btnPrev) btnPrev.onclick = () => cambiarDia(-1);

    const btnNext = document.getElementById('btnNextDay');
    if (btnNext) btnNext.onclick = () => cambiarDia(1);

    // Selector de fecha en vistas dedicadas
    const edif1DateSel = document.getElementById('edificio1DateSelector');
    if (edif1DateSel) edif1DateSel.onchange = renderEdificio1View;

    const edif2DateSel = document.getElementById('edificio2DateSelector');
    if (edif2DateSel) edif2DateSel.onchange = renderEdificio2View;

    const aldeaDateSel = document.getElementById('aldeaDateSelector');
    if (aldeaDateSel) aldeaDateSel.onchange = renderAldeaView;

    // Inicializar subida de archivos de layout
    initLayoutUpload();
}

// ====================================================================
// GESTIÓN DE SUBIDA DE ARCHIVOS DE LAYOUT (MÁXIMO 10 MB)
// ====================================================================
let pendingLayoutFile = null;
let pendingLayoutBase64 = null;

function initLayoutUpload() {
    const dropZone = document.getElementById('layoutDropZone');
    const fileInput = document.getElementById('reservaLayoutFile');
    const promptBox = document.getElementById('layoutUploadPrompt');
    const previewBox = document.getElementById('layoutFilePreview');
    const fileNameEl = document.getElementById('layoutFileName');
    const fileSizeEl = document.getElementById('layoutFileSize');
    const btnQuitar = document.getElementById('btnQuitarLayout');
    const btnVerActual = document.getElementById('btnVerLayoutActual');
    const urlHidden = document.getElementById('reservaLayoutUrl');
    const nombreHidden = document.getElementById('reservaLayoutNombre');

    if (!dropZone || !fileInput) return;

    // Clic en la zona activa el input file
    dropZone.onclick = (e) => {
        if (e.target.closest('#btnQuitarLayout') || e.target.closest('#btnVerLayoutActual')) return;
        fileInput.click();
    };

    // Drag and Drop
    dropZone.ondragover = (e) => {
        e.preventDefault();
        dropZone.style.borderColor = 'var(--tecmilenio-green)';
        dropZone.style.background = '#f0fdf4';
    };

    dropZone.ondragleave = (e) => {
        e.preventDefault();
        dropZone.style.borderColor = '#cbd5e1';
        dropZone.style.background = '#f8fafc';
    };

    dropZone.ondrop = (e) => {
        e.preventDefault();
        dropZone.style.borderColor = '#cbd5e1';
        dropZone.style.background = '#f8fafc';
        if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            handleFileSelected(e.dataTransfer.files[0]);
        }
    };

    // Selección por explorador de archivos
    fileInput.onchange = () => {
        if (fileInput.files && fileInput.files.length > 0) {
            handleFileSelected(fileInput.files[0]);
        }
    };

    // Botón quitar archivo
    if (btnQuitar) {
        btnQuitar.onclick = (e) => {
            e.stopPropagation();
            resetLayoutUploadUI();
            showToast('Archivo de layout removido.', 'info');
        };
    }

    function handleFileSelected(file) {
        const MAX_BYTES = 10 * 1024 * 1024; // 10 MB exactos
        if (file.size > MAX_BYTES) {
            showToast(`El archivo "${file.name}" pesa ${(file.size / (1024 * 1024)).toFixed(2)} MB, superando el límite máximo permitido de 10 MB.`, 'error');
            fileInput.value = '';
            return;
        }

        pendingLayoutFile = file;
        const reader = new FileReader();
        reader.onload = (ev) => {
            pendingLayoutBase64 = ev.target.result;
            if (urlHidden) urlHidden.value = ''; // Se creará la URL en el servidor al enviar
            if (nombreHidden) nombreHidden.value = file.name;

            if (fileNameEl) fileNameEl.textContent = file.name;
            if (fileSizeEl) fileSizeEl.textContent = formatBytes(file.size);
            if (btnVerActual) btnVerActual.style.display = 'none';

            if (promptBox) promptBox.style.display = 'none';
            if (previewBox) previewBox.style.display = 'flex';
            showToast(`Archivo "${file.name}" cargado (${formatBytes(file.size)}).`, 'success');
        };
        reader.onerror = () => {
            showToast('No se pudo leer el archivo seleccionado.', 'error');
        };
        reader.readAsDataURL(file);
    }
}

function resetLayoutUploadUI() {
    pendingLayoutFile = null;
    pendingLayoutBase64 = null;
    const fileInput = document.getElementById('reservaLayoutFile');
    if (fileInput) fileInput.value = '';
    const urlHidden = document.getElementById('reservaLayoutUrl');
    if (urlHidden) urlHidden.value = '';
    const nombreHidden = document.getElementById('reservaLayoutNombre');
    if (nombreHidden) nombreHidden.value = '';

    const promptBox = document.getElementById('layoutUploadPrompt');
    const previewBox = document.getElementById('layoutFilePreview');
    const btnVerActual = document.getElementById('btnVerLayoutActual');
    if (promptBox) promptBox.style.display = 'block';
    if (previewBox) previewBox.style.display = 'none';
    if (btnVerActual) btnVerActual.style.display = 'none';
}

function cargarLayoutEnEdicion(r) {
    pendingLayoutFile = null;
    pendingLayoutBase64 = null;
    const urlHidden = document.getElementById('reservaLayoutUrl');
    const nombreHidden = document.getElementById('reservaLayoutNombre');
    const promptBox = document.getElementById('layoutUploadPrompt');
    const previewBox = document.getElementById('layoutFilePreview');
    const fileNameEl = document.getElementById('layoutFileName');
    const fileSizeEl = document.getElementById('layoutFileSize');
    const btnVerActual = document.getElementById('btnVerLayoutActual');

    if (r && r.archivoLayout) {
        if (urlHidden) urlHidden.value = r.archivoLayout;
        if (nombreHidden) nombreHidden.value = r.archivoLayoutNombre || '';
        if (fileNameEl) fileNameEl.textContent = r.archivoLayoutNombre || 'Archivo de Layout adjunto';
        if (fileSizeEl) fileSizeEl.textContent = 'Archivo adjunto guardado';
        if (btnVerActual) {
            btnVerActual.href = r.archivoLayout;
            btnVerActual.style.display = 'inline-flex';
        }
        if (promptBox) promptBox.style.display = 'none';
        if (previewBox) previewBox.style.display = 'flex';
    } else {
        resetLayoutUploadUI();
    }
}

function formatBytes(bytes) {
    if (!bytes || bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// ====================================================================
// MODOS DE AGENDA Y TIMELINE CUADRÍCULA VISUAL
// ====================================================================
function setAgendaMode(mode) {
    state.agendaMode = mode;
    const btnTable = document.getElementById('btnModeTable');
    const btnTimeline = document.getElementById('btnModeTimeline');
    const contTable = document.getElementById('containerTableMode');
    const contTimeline = document.getElementById('containerTimelineMode');
    const dayNav = document.getElementById('timelineDayNav');

    if (mode === 'table') {
        if (btnTable) btnTable.classList.add('active');
        if (btnTimeline) btnTimeline.classList.remove('active');
        if (contTable) contTable.style.display = 'block';
        if (contTimeline) contTimeline.style.display = 'none';
        if (dayNav) dayNav.style.display = 'none';
    } else {
        if (btnTable) btnTable.classList.remove('active');
        if (btnTimeline) btnTimeline.classList.add('active');
        if (contTable) contTable.style.display = 'none';
        if (contTimeline) contTimeline.style.display = 'block';
        if (dayNav) dayNav.style.display = 'flex';
        renderTimelineGrid();
    }
}

function cambiarDia(delta) {
    const cur = new Date(state.timelineDate + 'T00:00:00');
    cur.setDate(cur.getDate() + delta);
    const yyyy = cur.getFullYear();
    const mm = String(cur.getMonth() + 1).padStart(2, '0');
    const dd = String(cur.getDate()).padStart(2, '0');
    state.timelineDate = `${yyyy}-${mm}-${dd}`;
    renderTimelineGrid();
}

function irAHoy() {
    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    state.timelineDate = `${yyyy}-${mm}-${dd}`;
    renderTimelineGrid();
}

function renderTimelineGrid() {
    const wrapper = document.getElementById('timelineGridWrapper');
    if (!wrapper) return;

    const dateVal = state.timelineDate || new Date().toISOString().split('T')[0];

    const lblDay = document.getElementById('lblTimelineActiveDay');
    if (lblDay) {
        lblDay.textContent = formatFecha(dateVal);
    }

    // Horas de 07:00 a 21:00
    const horas = [];
    for (let h = 7; h <= 21; h++) {
        horas.push(String(h).padStart(2, '0') + ':00');
    }

    const tipoFiltro = document.getElementById('filterTipoEspacio') ? document.getElementById('filterTipoEspacio').value : '';
    let espacios = state.espacios.filter(e => e.activo);
    if (tipoFiltro) {
        if (tipoFiltro === 'Edificio1') espacios = espacios.filter(isEdificio1);
        else if (tipoFiltro === 'Edificio2') espacios = espacios.filter(isEdificio2);
        else if (tipoFiltro === 'Aldea') espacios = espacios.filter(isAldea);
        else if (tipoFiltro === 'Mezanine') espacios = espacios.filter(isMezanine);
        else espacios = espacios.filter(e => e.tipoEspacio === tipoFiltro && !isMezanine(e));
    }
    if (state.searchQuery) {
        const q = state.searchQuery.toLowerCase();
        espacios = espacios.filter(e => e.nombreEspacio.toLowerCase().includes(q) || e.tipoEspacio.toLowerCase().includes(q));
    }

    // Ordenar: Edificio 1 primero, luego Edificio 2, luego Aldea, luego Mezanine, luego otros
    espacios.sort((a, b) => {
        const a1 = isEdificio1(a);
        const b1 = isEdificio1(b);
        if (a1 && !b1) return -1;
        if (!a1 && b1) return 1;

        const a2 = isEdificio2(a);
        const b2 = isEdificio2(b);
        if (a2 && !b2) return -1;
        if (!a2 && b2) return 1;

        const a3 = isAldea(a);
        const b3 = isAldea(b);
        if (a3 && !b3) return -1;
        if (!a3 && b3) return 1;

        const m1 = isMezanine(a);
        const m2 = isMezanine(b);
        if (m1 && !m2) return -1;
        if (!m1 && m2) return 1;

        return a.nombreEspacio.localeCompare(b.nombreEspacio, undefined, { numeric: true });
    });

    const reservasDelDia = state.reservas.filter(r => r.fechaReserva === dateVal && r.estadoReserva === 'Activa');

    let html = `
        <table class="timeline-table">
            <thead>
                <tr>
                    <th class="col-space">Salón / Espacio</th>
                    ${horas.map(h => `<th>${h}</th>`).join('')}
                </tr>
            </thead>
            <tbody>
    `;

    if (espacios.length === 0) {
        html += `<tr><td colspan="${horas.length + 1}" style="text-align:center;padding:32px;color:var(--text-muted)">No hay espacios para los filtros seleccionados.</td></tr>`;
    } else {
        espacios.forEach(e => {
            const reservasEspacio = reservasDelDia.filter(r => r.idEspacio === e.idEspacio);
            const badgeIcon = isMezanine(e) ? '📖' : isEdificio1(e) ? '🏢' : isEdificio2(e) ? '🏛️' : isAldea(e) ? '🌿' : e.tipoEspacio === 'Laboratorio' ? '🧪' : e.tipoEspacio === 'Cancha' ? '⚽' : '🌅';

            html += `
                <tr>
                    <td class="col-space">
                        <strong>${badgeIcon} Salón ${e.nombreEspacio}</strong>
                        <div style="font-size:0.7rem;color:var(--text-muted)">Cap: ${e.capacidad} | ${getEspacioEdificioLabel(e)}</div>
                    </td>
            `;

            horas.forEach(hora => {
                const horaMin = toMinutes(hora);
                const horaFinBloqueMin = horaMin + 60;

                const r = reservasEspacio.find(res => {
                    const rStart = toMinutes(res.horaInicio);
                    const rEnd = toMinutes(res.horaFin);
                    return horaMin < rEnd && horaFinBloqueMin > rStart;
                });

                if (r) {
                    let cardClass = '';
                    if (isEdificio1(e)) cardClass = 'is-edif1';
                    else if (isEdificio2(e)) cardClass = 'is-edif2';
                    else if (isAldea(e)) cardClass = 'is-aldea';
                    else if (isMezanine(e)) cardClass = 'is-mezanine';
                    else if (e.tipoEspacio === 'Otro') cardClass = 'is-otro';

                    html += `
                        <td class="timeline-slot">
                            <div class="slot-busy-block ${cardClass}" title="${escapeHtml(r.motivoEvento)} (${r.horaInicio}-${r.horaFin}) - ${escapeHtml(r.nombreUsuario || '')}" onclick="abrirEditarReserva(${r.idReserva})">
                                <span class="busy-title">${r.servicioMedico ? '🚑 ' : ''}${r.servicioTI ? '💻 ' : ''}${escapeHtml(r.motivoEvento)}</span>
                                <span class="busy-time">${r.horaInicio} - ${r.horaFin}</span>
                            </div>
                        </td>
                    `;
                } else if (isMezanine(e) && isWeekdayDate(dateVal) && horaMin < 15 * 60 && horaFinBloqueMin > 7 * 60) {
                    // Bloqueo visual estricto de Mezanine de Lunes a Viernes de 7:00 a 15:00
                    html += `
                        <td class="timeline-slot">
                            <div class="slot-busy-block" style="background:#fee2e2;border:1px dashed #ef4444;color:#991b1b;cursor:not-allowed;" title="Mezanine ocupado de lunes a viernes de 7:00 a 15:00. Disponible a partir de las 3:00 PM." onclick="showToast('Los cubículos de Mezanine están ocupados de 7:00 a 15:00 de lunes a viernes. Agendables a partir de las 3:00 PM.', 'warning')">
                                <span class="busy-title" style="font-size:0.7rem;font-weight:700;"><i class="ph-bold ph-lock"></i> Ocupado</span>
                                <span class="busy-time" style="font-size:0.65rem;">07:00 - 15:00</span>
                            </div>
                        </td>
                    `;
                } else {
                    const horaFinSugerida = String(parseInt(hora.split(':')[0]) + 1).padStart(2, '0') + ':00';
                    const hintLibre = isMezanine(e) ? 'Cubículo Mezanine libre. Clic para agendar después de las 3:00 PM' : `Salón libre. Clic para agendar a las ${hora}`;
                    html += `
                        <td class="timeline-slot">
                            <div class="slot-free" title="${hintLibre}" onclick="openReservaEnHorario(${e.idEspacio}, '${dateVal}', '${hora}', '${horaFinSugerida}')"></div>
                        </td>
                    `;
                }
            });

            html += `</tr>`;
        });
    }

    html += `</tbody></table>`;
    wrapper.innerHTML = html;
}

function exportarReservasCsv() {
    if (state.reservas.length === 0) {
        showToast('No hay reservas para exportar.', 'warning');
        return;
    }

    const headers = [
        'ID',
        'Fecha',
        'Hora Inicio',
        'Hora Fin',
        'Espacio',
        'Ubicacion / Edificio',
        'Evento / Motivo',
        'Solicitante / Profesor',
        'Correo Institucional',
        'Rol',
        'Estado',
        'Servicio Medico',
        'Soporte TI',
        'Archivo Layout',
        'Observaciones'
    ];

    const rows = state.reservas.map(r => {
        const esp = state.espacios.find(e => e.idEspacio === r.idEspacio);
        const ubicacion = esp ? getEspacioEdificioLabel(esp) : (r.edificioEspacio || r.tipoEspacio || 'General');

        return [
            r.idReserva,
            r.fechaReserva,
            r.horaInicio,
            r.horaFin,
            `"${(r.nombreEspacio || '').replace(/"/g, '""')}"`,
            `"${ubicacion.replace(/"/g, '""')}"`,
            `"${(r.motivoEvento || '').replace(/"/g, '""')}"`,
            `"${(r.nombreUsuario || '').replace(/"/g, '""')}"`,
            `"${(r.correoUsuario || '').replace(/"/g, '""')}"`,
            `"${(r.rolUsuario || '').replace(/"/g, '""')}"`,
            r.estadoReserva,
            r.servicioMedico ? '"SI"' : '"NO"',
            r.servicioTI ? '"SI"' : '"NO"',
            r.archivoLayout ? `"${(r.archivoLayoutNombre || r.archivoLayout).replace(/"/g, '""')}"` : '""',
            `"${(r.observaciones || '').replace(/"/g, '""')}"`
        ];
    });

    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map(row => row.join(','))].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `reservas_tecmilenio_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('¡Archivo Excel/CSV descargado exitosamente!', 'success');
}

// ====================================================================
// REPORTES INDIVIDUALES (WORD .DOC CON LOGO Y EXCEL .CSV)
// ====================================================================

function mostrarModalExitoReserva(idReserva) {
    const r = state.reservas.find(x => x.idReserva === idReserva);
    if (!r) return;

    const folio = `TM-CUAU-${String(r.idReserva).padStart(4, '0')}`;
    const elFolio = document.getElementById('exitoFolio');
    if (elFolio) elFolio.textContent = folio;

    const elEvento = document.getElementById('exitoEvento');
    if (elEvento) elEvento.textContent = r.motivoEvento;

    const elEspacio = document.getElementById('exitoEspacio');
    if (elEspacio) elEspacio.textContent = r.nombreEspacio;

    const elHorario = document.getElementById('exitoHorario');
    if (elHorario) elHorario.textContent = `${formatFecha(r.fechaReserva)} (${r.horaInicio} - ${r.horaFin})`;

    const elMed = document.getElementById('exitoServicioMedico');
    if (elMed) {
        elMed.textContent = r.servicioMedico ? '🚨 SÍ REQUERIDO' : 'No requerido';
        elMed.style.color = r.servicioMedico ? '#dc2626' : '#64748b';
    }

    const elTI = document.getElementById('exitoServicioTI');
    if (elTI) {
        elTI.textContent = r.servicioTI ? '💻 SÍ REQUERIDO' : 'No requerido';
        elTI.style.color = r.servicioTI ? '#2563eb' : '#64748b';
    }

    const rowLay = document.getElementById('exitoRowLayout');
    const linkLay = document.getElementById('exitoLayoutLink');
    if (rowLay && linkLay) {
        if (r.archivoLayout) {
            rowLay.style.display = 'flex';
            linkLay.href = r.archivoLayout;
            linkLay.textContent = '📎 ' + (r.archivoLayoutNombre || 'Ver Archivo');
        } else {
            rowLay.style.display = 'none';
        }
    }

    const btnWord = document.getElementById('btnDescargarWordModal');
    if (btnWord) btnWord.onclick = () => descargarReporteWord(r.idReserva);

    const btnExcel = document.getElementById('btnDescargarExcelModal');
    if (btnExcel) btnExcel.onclick = () => descargarReporteExcelIndividual(r.idReserva);

    openModal('modalExitoReserva');
}

function descargarReporteWord(idReserva) {
    const r = state.reservas.find(x => x.idReserva === idReserva);
    if (!r) {
        showToast('Reserva no encontrada para generar reporte', 'error');
        return;
    }

    const esp = state.espacios.find(e => e.idEspacio === r.idEspacio);
    const ubicacion = esp ? getEspacioEdificioLabel(esp) : (r.edificioEspacio || r.tipoEspacio || 'General');
    const folio = `TM-CUAU-${String(r.idReserva).padStart(4, '0')}`;
    const fechaLarga = formatFecha(r.fechaReserva);

    const profMatch = (r.observaciones || '').match(/Profesor:\s*([^|;\n]+)/i);
    const docente = profMatch ? profMatch[1].trim() : (r.rolUsuario === 'Profesor' ? r.nombreUsuario : 'No asignado');

    const logoHtml = (typeof LOGO_TECMILENIO_BASE64 !== 'undefined' && LOGO_TECMILENIO_BASE64)
        ? `<img src="${LOGO_TECMILENIO_BASE64}" width="220" style="max-height:65px;object-fit:contain;" alt="Tecmilenio Logo" />`
        : `<h2 style="color:#005934;margin:0;font-size:20pt;letter-spacing:1px;font-family:Arial,sans-serif;">TECMILENIO</h2>`;

    const html = `
    <html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
    <head>
        <meta charset='utf-8'>
        <title>Comprobante de Reserva ${folio}</title>
        <!--[if gte mso 9]>
        <xml>
            <w:WordDocument>
                <w:View>Print</w:View>
                <w:Zoom>100</w:Zoom>
                <w:DoNotOptimizeForBrowser/>
            </w:WordDocument>
        </xml>
        <![endif]-->
        <style>
            @page {
                size: letter portrait;
                margin: 2cm 2cm 2cm 2cm;
                mso-page-orientation: portrait;
            }
            body {
                font-family: 'Calibri', 'Arial', sans-serif;
                color: #1e293b;
                line-height: 1.4;
                font-size: 11pt;
            }
            .header-table {
                width: 100%;
                border-bottom: 3px solid #005934;
                padding-bottom: 12px;
                margin-bottom: 20px;
            }
            .title-box {
                text-align: right;
            }
            .title-main {
                color: #005934;
                font-size: 15pt;
                font-weight: bold;
                margin: 0;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }
            .title-sub {
                color: #475569;
                font-size: 10pt;
                margin: 3px 0 0 0;
                font-weight: 600;
            }
            .folio-badge {
                display: inline-block;
                background-color: #f0fdf4;
                border: 1px solid #86efac;
                color: #166534;
                padding: 4px 10px;
                font-weight: bold;
                font-size: 10.5pt;
                margin-top: 5px;
            }
            .section-title {
                background-color: #005934;
                color: #ffffff;
                font-size: 11pt;
                font-weight: bold;
                padding: 6px 10px;
                margin-top: 18px;
                margin-bottom: 10px;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }
            table.data-table {
                width: 100%;
                border-collapse: collapse;
                margin-bottom: 12px;
            }
            table.data-table th, table.data-table td {
                border: 1px solid #cbd5e1;
                padding: 8px 10px;
                text-align: left;
                vertical-align: middle;
                font-size: 10.5pt;
            }
            table.data-table th {
                background-color: #f8fafc;
                color: #334155;
                font-weight: bold;
                width: 28%;
            }
            .badge-urgent {
                background-color: #fee2e2;
                color: #991b1b;
                font-weight: bold;
                padding: 3px 8px;
                border: 1px solid #f87171;
            }
            .badge-normal {
                background-color: #f1f5f9;
                color: #475569;
                padding: 3px 8px;
            }
            .signatures-table {
                width: 100%;
                margin-top: 40px;
                border-collapse: collapse;
            }
            .signatures-table td {
                width: 33.33%;
                text-align: center;
                vertical-align: top;
                padding: 0 10px;
            }
            .sig-line {
                border-top: 1px solid #334155;
                padding-top: 6px;
                font-weight: bold;
                font-size: 9.5pt;
                color: #1e293b;
            }
            .sig-sub {
                font-size: 8.5pt;
                color: #64748b;
            }
            .footer-legal {
                margin-top: 30px;
                border-top: 1px solid #e2e8f0;
                padding-top: 8px;
                font-size: 8pt;
                color: #94a3b8;
                text-align: center;
            }
        </style>
    </head>
    <body>
        <table class="header-table" cellpadding="0" cellspacing="0">
            <tr>
                <td style="vertical-align:middle;">
                    ${logoHtml}
                </td>
                <td class="title-box" style="vertical-align:middle;">
                    <div class="title-main">Universidad Tecmilenio</div>
                    <div class="title-sub">Campus Cuautitlán • Control de Espacios</div>
                    <div class="folio-badge">FOLIO: ${folio}</div>
                </td>
            </tr>
        </table>

        <div style="text-align:center;margin-bottom:15px;">
            <h3 style="margin:0;font-size:13pt;color:#0f172a;text-transform:uppercase;">Comprobante Oficial de Reserva de Espacio</h3>
            <span style="font-size:9pt;color:#64748b;">Fecha de emisión: ${new Date().toLocaleDateString('es-ES', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}</span>
        </div>

        <div class="section-title">1. Datos Generales del Evento o Clase</div>
        <table class="data-table" cellpadding="0" cellspacing="0">
            <tr>
                <th>Nombre del Evento:</th>
                <td><strong style="color:#005934;font-size:11.5pt;">${escapeHtml(r.motivoEvento)}</strong></td>
            </tr>
            <tr>
                <th>Fecha Programada:</th>
                <td><strong>${fechaLarga}</strong> (${r.fechaReserva})</td>
            </tr>
            <tr>
                <th>Horario Reservado:</th>
                <td><strong>${r.horaInicio} hrs a ${r.horaFin} hrs</strong></td>
            </tr>
            <tr>
                <th>Estado de la Reserva:</th>
                <td><strong>${r.estadoReserva}</strong></td>
            </tr>
        </table>

        <div class="section-title">2. Espacio e Instalación Asignada</div>
        <table class="data-table" cellpadding="0" cellspacing="0">
            <tr>
                <th>Salón / Espacio:</th>
                <td><strong style="font-size:11pt;color:#005934;">${escapeHtml(r.nombreEspacio)}</strong></td>
            </tr>
            <tr>
                <th>Edificio / Ubicación:</th>
                <td>${escapeHtml(ubicacion)}</td>
            </tr>
            <tr>
                <th>Tipo de Espacio:</th>
                <td>${escapeHtml(r.tipoEspacio || 'General')}</td>
            </tr>
            <tr>
                <th>Capacidad Asignada:</th>
                <td>${r.capacidadRequerida || (esp ? esp.capacidad : 30)} personas aprox.</td>
            </tr>
        </table>

        <div class="section-title">3. Requerimientos Especiales (Servicio Médico y Soporte de TI)</div>
        <table class="data-table" cellpadding="0" cellspacing="0">
            <tr>
                <th>Servicio Médico:</th>
                <td>
                    ${r.servicioMedico 
                        ? `<span class="badge-urgent">🚨 SÍ REQUERIDO (Se notifica a Enfermería y Paramédico de Campus)</span>` 
                        : `<span class="badge-normal">No requerido para este evento</span>`}
                </td>
            </tr>
            <tr>
                <th>Soporte de TI:</th>
                <td>
                    ${r.servicioTI 
                        ? `<span class="badge-urgent" style="background:#eff6ff;color:#1e40af;border:1px solid #bfdbfe;">💻 SÍ REQUERIDO (Se notifica a Tecnologías de Información)</span>` 
                        : `<span class="badge-normal">No requerido para este evento</span>`}
                </td>
            </tr>
            <tr>
                <th>Observaciones / Acomodo:</th>
                <td>${escapeHtml(r.observaciones || 'Sin especificaciones extraordinarias.')}</td>
            </tr>
            <tr>
                <th>Archivo de Layout / Croquis:</th>
                <td>${r.archivoLayout ? `📎 <strong>${escapeHtml(r.archivoLayoutNombre || 'Layout Adjunto')}</strong> (<a href="${r.archivoLayout}" target="_blank">${r.archivoLayout}</a>)` : 'No se adjuntó archivo de layout para este evento.'}</td>
            </tr>
        </table>

        <div class="section-title">4. Responsable de la Solicitud</div>
        <table class="data-table" cellpadding="0" cellspacing="0">
            <tr>
                <th>Solicitado por:</th>
                <td><strong>${escapeHtml(r.nombreUsuario || 'Responsable')}</strong></td>
            </tr>
            <tr>
                <th>Rol Institucional:</th>
                <td>${escapeHtml(r.rolUsuario || 'Colaborador')}</td>
            </tr>
            <tr>
                <th>Correo Electrónico:</th>
                <td>${escapeHtml(r.correoUsuario || 'N/A')}</td>
            </tr>
            <tr>
                <th>Docente Asignado:</th>
                <td>${escapeHtml(docente)}</td>
            </tr>
        </table>

        <br/>
        <table class="signatures-table" cellpadding="0" cellspacing="0">
            <tr>
                <td>
                    <div style="height:50px;"></div>
                    <div class="sig-line">${escapeHtml(r.nombreUsuario || 'Responsable')}</div>
                    <div class="sig-sub">Solicitante Autorizado</div>
                </td>
                <td>
                    <div style="height:50px;"></div>
                    <div class="sig-line">Gestión de Espacios</div>
                    <div class="sig-sub">Campus Cuautitlán</div>
                </td>
                <td>
                    <div style="height:50px;"></div>
                    <div class="sig-line">Servicios Generales</div>
                    <div class="sig-sub">Apertura y Seguridad</div>
                </td>
            </tr>
        </table>

        <div class="footer-legal">
            Documento emitido por la Plataforma de Control de Espacios de Universidad Tecmilenio Campus Cuautitlán.<br/>
            Este comprobante ampara el uso exclusivo del espacio universitario en el día y horario señalado.
        </div>
    </body>
    </html>
    `;

    const blob = new Blob(['\uFEFF' + html], { type: 'application/msword;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `Reporte_Reserva_${folio}_${r.fechaReserva}.doc`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    showToast(`¡Reporte Word generado exitosamente: ${folio}!`, 'success');
}

function descargarReporteExcelIndividual(idReserva) {
    const r = state.reservas.find(x => x.idReserva === idReserva);
    if (!r) {
        showToast('Reserva no encontrada para generar reporte', 'error');
        return;
    }

    const esp = state.espacios.find(e => e.idEspacio === r.idEspacio);
    const ubicacion = esp ? getEspacioEdificioLabel(esp) : (r.edificioEspacio || r.tipoEspacio || 'General');
    const folio = `TM-CUAU-${String(r.idReserva).padStart(4, '0')}`;

    const headers = [
        'CAMPO',
        'DETALLE DE LA RESERVA'
    ];

    const data = [
        ['Folio Oficial', folio],
        ['Nombre del Evento / Clase', r.motivoEvento],
        ['Fecha de Reserva', r.fechaReserva],
        ['Hora Inicio', r.horaInicio],
        ['Hora Fin', r.horaFin],
        ['Salón / Espacio', r.nombreEspacio],
        ['Edificio / Área', ubicacion],
        ['Tipo de Espacio', r.tipoEspacio || 'General'],
        ['Capacidad Estimada', r.capacidadRequerida || (esp ? esp.capacidad : 30)],
        ['Estado de la Reserva', r.estadoReserva],
        ['Servicio Médico Requerido', r.servicioMedico ? 'SÍ REQUERIDO' : 'NO'],
        ['Soporte de TI Requerido', r.servicioTI ? 'SÍ REQUERIDO' : 'NO'],
        ['Archivo de Layout Adjunto', r.archivoLayout ? `${r.archivoLayoutNombre || 'Layout'} (${r.archivoLayout})` : 'NO'],
        ['Responsable Solicitante', r.nombreUsuario || 'Responsable'],
        ['Rol del Solicitante', r.rolUsuario || 'Colaborador'],
        ['Correo Institucional', r.correoUsuario || 'N/A'],
        ['Observaciones y Montaje', r.observaciones || 'Sin especificaciones'],
        ['Fecha de Generación del Reporte', new Date().toISOString()]
    ];

    const csvContent = '\uFEFF' + [
        'FICHA TÉCNICA INDIVIDUAL DE RESERVA - UNIVERSIDAD TECMILENIO CAMPUS CUAUTITLÁN',
        headers.join(','),
        ...data.map(([campo, val]) => `"${String(campo).replace(/"/g, '""')}","${String(val).replace(/"/g, '""')}"`)
    ].join('\r\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `Ficha_Reserva_${folio}_${r.fechaReserva}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    showToast(`¡Ficha Excel generada exitosamente: ${folio}!`, 'success');
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// ====================================================================
// TOAST DE NOTIFICACIONES Y UTILIDADES
// ====================================================================
function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    
    let icon = 'ph-info';
    if (type === 'success') icon = 'ph-check-circle';
    if (type === 'error') icon = 'ph-x-circle';
    if (type === 'warning') icon = 'ph-warning';

    toast.innerHTML = `<i class="ph-bold ${icon}"></i><span>${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
        setTimeout(() => toast.remove(), 300);
    }, 3800);
}

function formatFecha(f) {
    if (!f) return '';
    const parts = f.split('-');
    if (parts.length === 3) {
        return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return f;
}

// Fallback en memoria si el backend no está disponible
function loadMockFallbackData() {
    if (state.espacios.length === 0) {
        state.espacios = [
            { idEspacio: 1, nombreEspacio: 'Terraza', tipoEspacio: 'Otro', capacidad: 50, activo: true, edificio: 'General' },
            { idEspacio: 2, nombreEspacio: '2101', tipoEspacio: 'Aldea', capacidad: 30, activo: true, edificio: 'Edificio 2' },
            { idEspacio: 3, nombreEspacio: '2102', tipoEspacio: 'Aldea', capacidad: 30, activo: true, edificio: 'Edificio 2' },
            { idEspacio: 4, nombreEspacio: '2103', tipoEspacio: 'Aldea', capacidad: 30, activo: true, edificio: 'Edificio 2' },
            { idEspacio: 14, nombreEspacio: '2113', tipoEspacio: 'Aldea', capacidad: 30, activo: true, edificio: 'Edificio 2' },
            { idEspacio: 15, nombreEspacio: '1103', tipoEspacio: 'Edificio1', capacidad: 30, activo: true, edificio: 'Edificio 1' },
            { idEspacio: 16, nombreEspacio: '1104', tipoEspacio: 'Edificio1', capacidad: 30, activo: true, edificio: 'Edificio 1' },
            { idEspacio: 17, nombreEspacio: '1201', tipoEspacio: 'Edificio1', capacidad: 30, activo: true, edificio: 'Edificio 1' },
            { idEspacio: 18, nombreEspacio: '1203', tipoEspacio: 'Edificio1', capacidad: 30, activo: true, edificio: 'Edificio 1' },
            { idEspacio: 19, nombreEspacio: '1308', tipoEspacio: 'Edificio1', capacidad: 30, activo: true, edificio: 'Edificio 1' },
            { idEspacio: 20, nombreEspacio: '1310', tipoEspacio: 'Edificio1', capacidad: 30, activo: true, edificio: 'Edificio 1' },
            { idEspacio: 31, nombreEspacio: 'CILSE1', tipoEspacio: 'Laboratorio', capacidad: 30, activo: true, edificio: 'General' },
            { idEspacio: 37, nombreEspacio: 'Cancha Polivalente', tipoEspacio: 'Cancha', capacidad: 50, activo: true, edificio: 'General' }
        ];

        state.usuarios = [
            { idUsuario: 1, nombreCompleto: 'Diana Angelica Barrios Hernández', correo: 'diana.barrios@tecmilenio.mx', rol: 'Administrador' },
            { idUsuario: 2, nombreCompleto: 'Natalie Luna', correo: 'natalie.luna@tecmilenio.mx', rol: 'Responsable' },
            { idUsuario: 4, nombreCompleto: 'Jonathan Salazar', correo: 'jonathan.salazar@tecmilenio.mx', rol: 'Profesor' }
        ];

        state.reservas = [
            { idReserva: 1, idUsuario: 2, idEspacio: 1, fechaReserva: '2026-10-06', horaInicio: '10:00', horaFin: '12:00', motivoEvento: 'Conferencia de Violencia Digital', estadoReserva: 'Completada', nombreEspacio: 'Terraza', tipoEspacio: 'Otro', nombreUsuario: 'Natalie Luna' }
        ];
    }
    renderAllViews();
}
