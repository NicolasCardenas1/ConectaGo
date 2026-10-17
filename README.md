# ConectaGo — LIMS · Sistema de Gestión de Información de Laboratorio

Proyecto APT (Capstone) — **DuocUC**, Escuela de Informática y Telecomunicaciones.

Aplicación de escritorio para **investigadores que realizan muestreos ambientales** (por ejemplo, de **algas**) y necesitan **conservar y trazar sus muestras durante largos periodos**. Hoy ese registro se lleva en papel; el LIMS lo digitaliza: recepción con **firma de cadena de custodia**, almacenamiento, análisis con **validación contra rangos normales**, aprobación por un supervisor e **informe PDF dirigido al investigador** que encargó el muestreo. Trazabilidad orientada a **ISO/IEC 17025**.

> **Qué no es:** no es un sistema clínico. No hay pacientes ni perfiles bioquímicos.
>
> **Alcance:** versión de 12 semanas (JavaFX + Spring Boot + MySQL) de una propuesta original de 8 meses.

| | |
|---|---|
| **Equipo** | Claudio Murúa (Product Owner) · Nicolás Cárdenas (Scrum Master) · Cristian Solís |
| **Docente** | Anibal Faúndez |
| **Metodología** | Scrum — 6 sprints de 2 semanas |
| **Estado** | RF01 a RF06 implementados · en etapa de pruebas, instalador y documentación final |

---

## Funcionalidades

| Requisito | Descripción | Estado |
|---|---|:-:|
| **RF01** | Registro de muestras con **código único** (`M-2026-0001`), investigador, sitio y fecha de muestreo, almacenamiento y estados de workflow | ✅ |
| **RF02** | Asignación de análisis a cada muestra e **ingreso de resultados** por el analista | ✅ |
| **RF03** | **Validación automática de rangos**: aviso en vivo y justificación obligatoria si el valor está fuera de rango | ✅ |
| **RF04** | **Aprobación o rechazo** por un supervisor (nunca de su propio resultado, una sola evaluación) | ✅ |
| **RF05** | **Informe de resultados en PDF** para el investigador (versionado, con vista previa y descarga) | ✅ |
| **RF06** | **Autenticación con roles** (Analista, Supervisor, Administrador): sesión con token y permisos por rol | ✅ |
| Extra | **Cadena de custodia firmada**: cada entrega, traslado o retiro queda confirmado por quien recibe, con fecha y hora | ✅ |
| Extra | **Historial de estados** de cada muestra (quién, cuándo, de qué estado a cuál y por qué) | ✅ |
| Extra | **Tabla de parámetros y rangos normales** visible en la aplicación | ✅ |

### Flujo de una muestra

```
Recibida ─► En análisis ─► Resultados ingresados ─► Aprobada ─► Reportada (informe PDF)
                                               └──► Rechazada
```

### Roles

| Rol | Puede |
|---|---|
| **Analista** | Registrar investigadores y muestras, firmar la cadena de custodia, asignar análisis e ingresar resultados |
| **Supervisor** | Todo lo anterior + aprobar/rechazar resultados y emitir informes PDF |
| **Administrador** | Todo lo anterior + administrar usuarios, contraseñas y el catálogo de parámetros y rangos |

---

## Tecnologías

| Capa | Tecnología |
|---|---|
| Cliente de escritorio | **Java 21 + JavaFX 21** (FXML + CSS), HttpClient de Java, Jackson |
| Backend (API REST) | **Spring Boot 4**, Spring Data JPA (Hibernate), Spring Security, Bean Validation, Lombok |
| Base de datos | **MySQL 8** — local con Docker o compartida en la nube (Aiven) |
| Seguridad | Contraseñas con **BCrypt**, sesión con token *Bearer*, permisos por rol |
| Informes | **OpenPDF** |
| Herramientas | IntelliJ IDEA, Maven Wrapper, Docker Desktop, Git/GitHub |

### Arquitectura

```
┌──────────────────────┐    HTTP + JSON     ┌──────────────────────────┐    JDBC    ┌───────────┐
│  Cliente JavaFX      │  Authorization:    │  Backend Spring Boot     │ ─────────► │  MySQL 8  │
│  (escritorio)        │  Bearer <token>    │  controller → service →  │            │ (Docker / │
│  pantallas FXML      │ ─────────────────► │  repository (JPA)        │            │  Aiven)   │
│  ApiClient           │ ◄───────────────── │  security · PDF          │            └───────────┘
└──────────────────────┘                    └──────────────────────────┘
```

---

## Estructura del repositorio

```
ConectaGo/
├── README.md                      ← este archivo (portada del proyecto)
├── Fase1/
│   ├── Evidencias grupales/       ← definición del proyecto APT, Carta Gantt, presentación
│   └── Evidencias individuales/   ← autoevaluaciones
└── Evidencia Proyecto/
    └── lims/
        ├── README.md              ← guía técnica completa (instalación, endpoints, problemas comunes)
        ├── README-aiven.md        ← conexión a la base de datos compartida en la nube
        ├── lims-backend/          ← API REST Spring Boot + esquema SQL (db-init/), migraciones (db-migrations/) y test.http
        └── lims-client/           ← aplicación de escritorio JavaFX
```

---

## Inicio rápido

Requisitos: **JDK 21**, **Docker Desktop**, **Git** e IntelliJ IDEA (recomendado).

```powershell
git clone https://github.com/NicolasCardenas1/ConectaGo.git
cd "ConectaGo/Evidencia Proyecto/lims"

# 1) Base de datos (Docker Desktop abierto)
cd lims-backend
docker-compose up -d

# 2) Backend
.\mvnw.cmd spring-boot:run          # espera "Started LimsBackendApplication"

# 3) Cliente (en otra terminal)
cd ..\lims-client
.\mvnw.cmd javafx:run
```

En una base de datos nueva, el backend crea el usuario administrador **`admin` / `admin123`**. Si usan una base que ya existía (por ejemplo Aiven), primero ejecuten la migración `lims-backend/db-migrations/v4-dominio-ambiental.sql`. Desde ahí se crean los Supervisores y Analistas en la pantalla *Usuarios* (cambien esa contraseña al entrar).

➡️ La guía completa —configuración, base en la nube, endpoints de la API, datos de prueba con `test.http` y solución de problemas— está en **[Evidencia Proyecto/lims/README.md](Evidencia%20Proyecto/lims/README.md)**.

---

## Pantallas principales

| Pantalla | Descripción |
|---|---|
| Inicio de sesión | Login, registro (como Analista) y "¿Olvidaste tu contraseña?" |
| Listado de muestras | Tabla con estados en color y buscador en vivo |
| Registro de muestra | Investigador, sitio y fecha de toma, almacenamiento y firma de recepción; genera el código único |
| Detalle de muestra | Datos, análisis, ingreso de resultados, historial, **cadena de custodia** e informe PDF |
| Investigadores | Quienes encargan los muestreos y reciben los informes |
| Parámetros y rangos | Tabla de rangos normales por parámetro (el Administrador la mantiene) |
| Aprobaciones | Resultados pendientes con panel de revisión (Supervisor / Administrador) |
| Usuarios | Crear usuarios con rol, resetear contraseñas, activar/desactivar (Administrador) |

---

## Próximos pasos

- Flujo de reingreso de un resultado después de un rechazo.
- Pruebas unitarias JUnit de los servicios.
- Cargar los parámetros reales de algas (clorofila-a, salinidad, pH, temperatura, nutrientes) con su fuente.
- Instalador (`.exe`), video demo, informe final y presentación.
- HTTPS para una instalación en producción (RNF04).
