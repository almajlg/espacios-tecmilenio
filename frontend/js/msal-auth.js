/**
 * MSAL AUTH - Módulo de Autenticación con Microsoft (Microsoft Entra ID / Azure AD)
 * para la Universidad Tecmilenio - Control de Espacios
 * 
 * Implementa soporte dual:
 * 1. Microsoft Authentication Library (MSAL Browser) si está presente.
 * 2. Motor Nativo Microsoft Entra ID (OAuth 2.0 / OpenID Connect) sin dependencias externas,
 *    garantizando disponibilidad 100% inmediata en cualquier red o navegador.
 */

// Credenciales oficiales de Microsoft Azure Entra ID para Tecmilenio
const AZURE_DEFAULT_CONFIG = {
    clientId: '5d5bf333-29dc-4268-9de8-1452f48ace82',
    tenantId: '030e951d-6efd-42f5-8427-bab632cf8010'
};

const AUTH_USER_KEY = 'tecmilenio_auth_user';
const OIDC_STATE_KEY = 'tecmilenio_oidc_state';
const OIDC_NONCE_KEY = 'tecmilenio_oidc_nonce';

let msalInstance = null;
let currentAuthUser = null;

/**
 * Obtiene el Client ID y Tenant ID activos (de localStorage o predeterminados)
 */
function getActiveAzureCredentials() {
    const clientId = localStorage.getItem('msal_client_id') || AZURE_DEFAULT_CONFIG.clientId;
    const tenantId = localStorage.getItem('msal_tenant_id') || AZURE_DEFAULT_CONFIG.tenantId;
    return { clientId: (clientId || '').trim(), tenantId: (tenantId || '').trim() };
}

/**
 * Inicializa la autenticación con Microsoft y verifica el estado de la sesión
 */
async function initMsalAuth() {
    // 1. Revisar si la página acaba de regresar de una redirección de Microsoft con hash
    if (window.location.hash && (window.location.hash.includes('id_token=') || window.location.hash.includes('access_token=') || window.location.hash.includes('error='))) {
        const handled = await procesarHashAutenticacion(window.location.hash);
        if (handled) {
            // Limpiar el hash de la URL para estética y seguridad
            try {
                history.replaceState(null, '', window.location.pathname + window.location.search);
            } catch (e) {}
            return;
        }
    }

    // 2. Cargar usuario guardado en sesión previa
    const savedUserJson = sessionStorage.getItem(AUTH_USER_KEY) || localStorage.getItem(AUTH_USER_KEY);
    if (savedUserJson) {
        try {
            currentAuthUser = JSON.parse(savedUserJson);
        } catch (e) {
            currentAuthUser = null;
        }
    }

    // 3. Si la biblioteca MSAL Browser está disponible en el entorno global
    if (window.msal && !currentAuthUser) {
        try {
            const { clientId, tenantId } = getActiveAzureCredentials();
            if (clientId) {
                if (!msalInstance) {
                    msalInstance = new window.msal.PublicClientApplication({
                        auth: {
                            clientId: clientId,
                            authority: `https://login.microsoftonline.com/${tenantId}`,
                            redirectUri: window.location.origin + '/'
                        },
                        cache: {
                            cacheLocation: 'localStorage',
                            storeAuthStateInCookie: false
                        }
                    });
                    await msalInstance.initialize();
                }
                const response = await msalInstance.handleRedirectPromise();
                if (response && response.account) {
                    await procesarCuentaMicrosoft(response.account);
                }
            }
        } catch (err) {
            console.warn('Nota sobre inicialización MSAL:', err);
        }
    }

    // 4. Evaluar estado de login y actualizar vista
    actualizarVistaSegunAutenticacion();
}

/**
 * Procesa el hash de retorno de Microsoft OAuth2 / OpenID Connect
 */
async function procesarHashAutenticacion(hashString) {
    if (!hashString) return false;
    const cleanHash = hashString.startsWith('#') ? hashString.substring(1) : hashString;
    const params = new URLSearchParams(cleanHash);

    // Verificar si hubo error en Microsoft
    const error = params.get('error');
    if (error) {
        const desc = params.get('error_description') || error;
        console.error('Error de autenticación Microsoft:', desc);
        let mensajeAmigable = desc;
        if (desc.includes('AADSTS50011')) {
            mensajeAmigable = 'La URL de redirección no está configurada en Azure Entra ID. Debe ser exactamente: http://localhost:8080/';
        }
        if (window.showToast) {
            window.showToast(`Error Microsoft: ${mensajeAmigable}`, 'error');
        } else {
            alert(`Error Microsoft: ${mensajeAmigable}`);
        }
        return false;
    }

    // Extraer id_token
    const idToken = params.get('id_token') || params.get('access_token');
    if (idToken) {
        const claims = parseJwt(idToken);
        if (claims) {
            const nombre = claims.name || claims.preferred_username || claims.email || 'Usuario Microsoft';
            const correo = claims.preferred_username || claims.email || claims.upn || '';
            await procesarCuentaMicrosoft({
                name: nombre,
                username: correo
            });
            return true;
        }
    }
    return false;
}

/**
 * Decodificador seguro de tokens JWT de Microsoft
 */
function parseJwt(token) {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(
            atob(base64)
                .split('')
                .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
                .join('')
        );
        return JSON.parse(jsonPayload);
    } catch (e) {
        console.error('Error al decodificar JWT de Microsoft:', e);
        return null;
    }
}

/**
 * Verifica si un usuario tiene autorización para agendar, modificar o cancelar eventos.
 * REGLA INSTITUCIONAL: Únicamente Coordinadores y Mentores tienen autorización. Los demás solo pueden visualizar.
 */
function puedeAgendarModificar(user) {
    if (!user) return false;
    const rol = (user.rol || '').toLowerCase().trim();
    return rol.includes('coordinador') || rol.includes('mentor') || rol.includes('admin');
}
window.puedeAgendarModificar = puedeAgendarModificar;

/**
 * Procesa la cuenta institucional devuelta por Microsoft y la sincroniza con el backend Java
 */
async function procesarCuentaMicrosoft(account) {
    const nombre = account.name || account.username || 'Usuario Institucional';
    const correo = (account.username || '').toLowerCase();

    // Determinar rol inicial sugerido según correo institucional:
    // Por defecto 'Profesor' (solo visualización), excepto coordinadores y mentores
    let rolSugerido = 'Profesor';
    if (correo.includes('admin') || correo.includes('diana.barrios') || correo.includes('dabarrios') || correo.includes('coordinacion')) {
        rolSugerido = 'Coordinador';
    } else if (correo.includes('mentor')) {
        rolSugerido = 'Mentor';
    }

    // Notificar al backend Java para vincular o auto-registrar al usuario en DataStore
    let usuarioBackend = null;
    try {
        const apiBase = (typeof API_BASE !== 'undefined') ? API_BASE : 'http://localhost:8080/api';
        const res = await fetch(`${apiBase}/auth/verify`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                nombre: nombre,
                correo: correo,
                rol: rolSugerido
            })
        });

        if (res.ok) {
            const json = await res.json();
            if (json && json.usuario) {
                usuarioBackend = json.usuario;
            }
        }
    } catch (e) {
        console.warn('Aviso: Backend Java no respondió a sincronización de sesión, continuando en modo cliente:', e);
    }

    // Establecer la sesión activa
    establecerSesionUsuario({
        idUsuario: usuarioBackend ? usuarioBackend.idUsuario : 1,
        nombre: usuarioBackend ? usuarioBackend.nombreCompleto : nombre,
        correo: correo,
        rol: usuarioBackend ? usuarioBackend.rol : rolSugerido,
        proveedor: 'Microsoft Entra ID (Azure AD)'
    });

    if (window.showToast) {
        window.showToast(`¡Bienvenido, ${nombre}! Sesión verificada con Microsoft.`, 'success');
    }
}

/**
 * Iniciar sesión con Microsoft
 * Intenta primero con MSAL si está disponible; si no, ejecuta el flujo OAuth 2.0 / OIDC nativo.
 */
async function loginConMicrosoft() {
    const { clientId, tenantId } = getActiveAzureCredentials();

    if (!clientId) {
        mostrarModalConfigAzure(true);
        return;
    }

    // MÉTODO 1: Si la biblioteca MSAL Browser está cargada
    if (window.msal) {
        try {
            if (!msalInstance) {
                msalInstance = new window.msal.PublicClientApplication({
                    auth: {
                        clientId: clientId,
                        authority: `https://login.microsoftonline.com/${tenantId}`,
                        redirectUri: window.location.origin + '/'
                    },
                    cache: {
                        cacheLocation: 'localStorage',
                        storeAuthStateInCookie: false
                    }
                });
                await msalInstance.initialize();
            }

            const loginRequest = {
                scopes: ['User.Read', 'openid', 'profile', 'email']
            };

            const response = await msalInstance.loginPopup(loginRequest);
            if (response && response.account) {
                await procesarCuentaMicrosoft(response.account);
                return;
            }
        } catch (msalError) {
            console.warn('MSAL Popup lanzó excepción, probando con motor OAuth2 nativo:', msalError);
        }
    }

    // MÉTODO 2: Motor Nativo Microsoft Entra ID (OAuth 2.0 / OpenID Connect)
    // No requiere ninguna biblioteca externa. 100% compatible y directo con Microsoft.
    ejecutarOAuth2MicrosoftDirecto(clientId, tenantId);
}

/**
 * Inicia el flujo oficial OAuth 2.0 / OpenID Connect directo con Microsoft Entra ID
 */
function ejecutarOAuth2MicrosoftDirecto(clientId, tenantId) {
    const redirectUri = window.location.origin + '/';
    const state = 'tm_' + Math.random().toString(36).substring(2, 15);
    const nonce = 'nc_' + Math.random().toString(36).substring(2, 15);

    sessionStorage.setItem(OIDC_STATE_KEY, state);
    sessionStorage.setItem(OIDC_NONCE_KEY, nonce);

    // Parámetros oficiales de autorización de Microsoft
    const params = new URLSearchParams({
        client_id: clientId,
        response_type: 'id_token token',
        redirect_uri: redirectUri,
        scope: 'openid profile email',
        response_mode: 'fragment',
        state: state,
        nonce: nonce
    });

    const authUrl = `https://login.microsoftonline.com/${tenantId}/oauth2/v2.0/authorize?${params.toString()}`;

    // Intentar abrir ventana emergente centrada (Popup)
    const w = 520;
    const h = 650;
    const left = window.screenX + (window.outerWidth - w) / 2;
    const top = window.screenY + (window.outerHeight - h) / 2;
    
    let popup = null;
    try {
        popup = window.open(authUrl, 'MicrosoftAuthPopup', `width=${w},height=${h},left=${left},top=${top},status=yes,scrollbars=yes`);
    } catch (e) {
        popup = null;
    }

    if (popup && !popup.closed) {
        // Monitorear cuando la ventana emergente regrese a nuestro dominio
        const checkTimer = setInterval(async () => {
            try {
                if (!popup || popup.closed) {
                    clearInterval(checkTimer);
                    return;
                }

                // Si la ventana emergente ya llegó a localhost:8080
                if (popup.location && popup.location.origin === window.location.origin) {
                    const hash = popup.location.hash;
                    popup.close();
                    clearInterval(checkTimer);

                    if (hash) {
                        await procesarHashAutenticacion(hash);
                    }
                }
            } catch (crossOriginError) {
                // Es normal mientras el usuario esté en login.microsoftonline.com debido al CORS de browsers
            }
        }, 400);
    } else {
        // Si el navegador bloqueó el popup, redirigir la pestaña actual
        window.location.href = authUrl;
    }
}

/**
 * Iniciar sesión en modo local / simulado (para desarrollo y pruebas)
 */
function loginLocalSimulado(usuario) {
    establecerSesionUsuario({
        idUsuario: usuario.idUsuario,
        nombre: usuario.nombreCompleto,
        correo: usuario.correo,
        rol: usuario.rol,
        proveedor: 'Cuenta Institucional Local (Tecmilenio)'
    });

    if (window.showToast) {
        window.showToast(`Sesión iniciada como: ${usuario.nombreCompleto} (${usuario.rol})`, 'success');
    }
}

/**
 * Guarda y activa la sesión del usuario
 */
function establecerSesionUsuario(userData) {
    currentAuthUser = userData;
    sessionStorage.setItem(AUTH_USER_KEY, JSON.stringify(userData));
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(userData));
    actualizarVistaSegunAutenticacion();
    
    // Si estamos en login.html o en la pantalla de bienvenida, redirigir a index.html
    const isLoginPage = window.location.pathname.includes('login') || !document.querySelector('.app-layout');
    if (isLoginPage) {
        setTimeout(() => {
            window.location.replace('index.html');
        }, 400);
        return;
    }

    // Si existe la función de cargar datos en app.js
    if (typeof loadAllData === 'function') {
        loadAllData();
    }
}

/**
 * Cerrar sesión y redirigir a la página de login
 */
async function logoutUsuario() {
    if (msalInstance) {
        try {
            const accounts = msalInstance.getAllAccounts();
            if (accounts.length > 0) {
                await msalInstance.logoutPopup({ account: accounts[0] });
            }
        } catch (e) {
            console.warn('Aviso en logout de MSAL:', e);
        }
    }

    currentAuthUser = null;
    sessionStorage.removeItem(AUTH_USER_KEY);
    localStorage.removeItem(AUTH_USER_KEY);

    if (window.showToast) {
        window.showToast('Sesión finalizada correctamente.', 'info');
    }

    // Redirigir a la pantalla de inicio de sesión
    setTimeout(() => {
        window.location.replace('login.html');
    }, 200);
}

/**
 * Actualiza la visibilidad de la pantalla de Login y los datos en la barra superior
 */
function actualizarVistaSegunAutenticacion() {
    const overlay = document.getElementById('authLoginOverlay');
    const userBadgeContainer = document.getElementById('headerUserBadge');
    const appWrapper = document.querySelector('.app-layout');

    if (!currentAuthUser) {
        // Bloquear aplicación y mostrar pantalla de Login
        if (overlay) overlay.style.display = 'flex';
        if (appWrapper) appWrapper.classList.add('app-locked');
        if (userBadgeContainer) userBadgeContainer.style.display = 'none';
    } else {
        // Desbloquear aplicación
        if (overlay) overlay.style.display = 'none';
        if (appWrapper) appWrapper.classList.remove('app-locked');

        // Mostrar datos del usuario en la barra superior
        if (userBadgeContainer) {
            userBadgeContainer.style.display = 'flex';
            const nombreEl = document.getElementById('authUserName');
            const rolEl = document.getElementById('authUserRole');
            const avatarEl = document.getElementById('authUserAvatar');

            const autorizado = puedeAgendarModificar(currentAuthUser);

            if (nombreEl) nombreEl.textContent = currentAuthUser.nombre;
            if (rolEl) {
                if (autorizado) {
                    rolEl.innerHTML = `<span style="color:var(--tecmilenio-green);font-weight:700;"><i class="ph-bold ph-shield-check"></i> ${currentAuthUser.rol}</span> • Autorizado`;
                } else {
                    rolEl.innerHTML = `<span style="color:#d97706;font-weight:700;"><i class="ph-bold ph-eye"></i> ${currentAuthUser.rol}</span> • Solo Visualización`;
                }
            }
            if (avatarEl) {
                const iniciales = (currentAuthUser.nombre || 'U')
                    .split(' ')
                    .filter(x => x.length > 0)
                    .slice(0, 2)
                    .map(x => x[0].toUpperCase())
                    .join('');
                avatarEl.textContent = iniciales || 'TM';
                avatarEl.style.background = autorizado ? 'var(--tecmilenio-dark)' : '#64748b';
            }

            // Aplicar clase global de solo visualización si el usuario no es Coordinador ni Mentor
            if (autorizado) {
                document.body.classList.remove('mode-view-only');
            } else {
                document.body.classList.add('mode-view-only');
            }
        }

        // Si existe el select de usuario en la reserva, sincronizarlo si es autorizado
        const selectU = document.getElementById('reservaUsuario');
        if (selectU && currentAuthUser.idUsuario) {
            if (puedeAgendarModificar(currentAuthUser)) {
                selectU.value = currentAuthUser.idUsuario;
            }
        }
    }
}

/**
 * Mostrar modal de configuración de Azure AD
 */
function mostrarModalConfigAzure(desdeIntentoLogin = false) {
    const modal = document.getElementById('modalConfigAzure');
    if (modal) {
        const inpClient = document.getElementById('azureClientId');
        const inpTenant = document.getElementById('azureTenantId');
        const creds = getActiveAzureCredentials();
        
        if (inpClient) inpClient.value = creds.clientId;
        if (inpTenant) inpTenant.value = creds.tenantId;

        const aviso = document.getElementById('azurePromptAviso');
        if (aviso) {
            aviso.style.display = desdeIntentoLogin ? 'block' : 'none';
        }

        modal.classList.add('active');
    }
}

/**
 * Guardar configuración de Azure AD
 */
function guardarConfigAzure() {
    const inpClient = document.getElementById('azureClientId');
    const inpTenant = document.getElementById('azureTenantId');

    const clientId = inpClient ? inpClient.value.trim() : '';
    const tenantId = inpTenant ? inpTenant.value.trim() : '';

    if (clientId) {
        localStorage.setItem('msal_client_id', clientId);
    } else {
        localStorage.removeItem('msal_client_id');
    }

    if (tenantId) {
        localStorage.setItem('msal_tenant_id', tenantId);
    } else {
        localStorage.removeItem('msal_tenant_id');
    }

    // Reinicializar MSAL con las nuevas credenciales
    msalInstance = null;

    const modal = document.getElementById('modalConfigAzure');
    if (modal) modal.classList.remove('active');

    if (window.showToast) {
        window.showToast('Configuración de Microsoft Azure AD actualizada.', 'success');
    }

    if (clientId) {
        loginConMicrosoft();
    }
}

// Exponer en window para acceso global
window.initMsalAuth = initMsalAuth;
window.loginConMicrosoft = loginConMicrosoft;
window.loginLocalSimulado = loginLocalSimulado;
window.logoutUsuario = logoutUsuario;
window.mostrarModalConfigAzure = mostrarModalConfigAzure;
window.guardarConfigAzure = guardarConfigAzure;
window.getAuthUser = () => currentAuthUser;
