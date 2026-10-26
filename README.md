# ConectaGo — LIMS · Sistema de Gestión de Información de Laboratorio

Proyecto APT (Capstone) — **DuocUC**, Escuela de Informática y Telecomunicaciones.

Aplicación de escritorio para **investigadores que realizan muestreos ambientales** (por ejemplo, de **algas**) y necesitan **conservar y trazar sus muestras durante largos periodos**. Hoy ese registro se lleva en papel; el LIMS lo digitaliza: recepción con **firma de cadena de custodia**, almacenamiento, análisis con **validación contra rangos normales**, aprobación por un supervisor e **informe PDF dirigido al investigador** que encargó el muestreo. Trazabilidad orientada a **ISO/IEC 17025**.

> **Qué no es:** no es un sistema clínico. No hay pacientes ni perfiles bioquímicos.
>
> **Alcance:** versión de 12 semanas (JavaFX + Spring Boot + MySQL) de una propuesta original de 8 meses.

## Qué entregamos

> **ConectaGo LIMS es una aplicación de escritorio para el laboratorio piloto de investigación ambiental de la Región de Los Lagos que garantiza la trazabilidad completa de cada muestra y de cada dato, desde el sitio de muestreo georreferenciado hasta el informe aprobado que recibe el investigador.**

| | |
|---|---|
| **Problema** | Hoy las muestras de algas y agua se registran en papel y planillas: se pierde quién midió qué, con qué equipo, qué valor se corrigió y por qué, y no se detectan valores anómalos a tiempo. |
| **Usuarios** | Analista (registra, mide), Supervisor (revisa, aprueba, informa), Administrador (catálogos, equipos, usuarios). El **investigador** recibe el informe. |
| **Entregable final** | 1) Aplicación de escritorio (JavaFX) + API (Spring Boot) + base MySQL, con instalador. 2) Base de demostración con 8 meses de historial ambiental real de formato. 3) Manual de usuario y guía técnica ([Evidencia Proyecto/lims/README.md](Evidencia%20Proyecto/lims/README.md)). 4) Video y guion de demo ([GUION-DEMO.md](Evidencia%20Proyecto/lims/GUION-DEMO.md)). 5) Informe final APT. |
| **Fuera de alcance** | Multi-laboratorio, app móvil de terreno, integración automática con equipos, acreditación ISO 17025 formal (el sistema está *diseñado según* sus requisitos de trazabilidad, no certificado). |

## ¿Por qué un LIMS y no una planilla Excel?

| En una planilla Excel | En ConectaGo LIMS |
|---|---|
| Cualquiera edita cualquier celda | **Permisos por rol**: el analista mide, el supervisor aprueba, y nadie aprueba su propio resultado |
| Un valor se sobrescribe sin dejar rastro | **Cada resultado tiene versiones**: quién lo cambió, cuándo, con qué equipo y **por qué** (motivo obligatorio). Un dato aprobado no se puede modificar |
| No se sabe con qué instrumento se midió ni si estaba calibrado | Cada resultado queda asociado a un **equipo con calibración vigente**; uno vencido **no se puede usar** |
| El sitio es un texto escrito a mano | **Sitios georreferenciados** (latitud/longitud) en un **mapa**, y listas desplegables en vez de texto libre |
| Validación manual (si alguien la hace) | **Validación automática de rangos** + **detección de valores atípicos** contra el historial del sitio, en vivo |
| Un archivo por persona, versiones en conflicto | **Base central multiusuario**: todos ven el mismo estado en tiempo real |
| Sin flujo de trabajo | **Flujo con estados**: recepción → análisis → aprobación → rechazo/corrección → informe, con **cadena de custodia firmada** |
| Informe armado a mano | **Informe PDF versionado** con equipo, correcciones e **interpretación asistida por IA revisada por un supervisor** |
| "¿Quién cambió esto?" no tiene respuesta | **Trazabilidad completa** de cada muestra en una línea de tiempo (requisito central de ISO/IEC 17025) |

| | |
|---|---|
| **Equipo** | Claudio Murúa (Product Owner) · Nicolás Cárdenas (Scrum Master) · Cristian Solís |
| **Docente** | Anibal Faúndez |
| **Metodología** | Scrum — 6 sprints de 2 semanas |
| **Estado** | RF01 a RF06 implementados + **v5** (correcciones de la retroalimentación de Fase 2) · en etapa de pruebas, instalador y documentación final |

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
| **v5** | **Trazabilidad del dato**: versiones de cada resultado con motivo del cambio, rechazo → corrección → nueva aprobación, y línea de tiempo completa de la muestra | ✅ |
| **v5** | **Equipos con calibración**: cada resultado indica el equipo; uno con calibración vencida no se puede usar | ✅ |
| **v5** | **Sitios de muestreo georreferenciados** con **mapa** interactivo (OpenStreetMap) | ✅ |
| **v5** | **Datos desplegables**: sitio, equipo, ubicación de almacenamiento y condición de recepción salen de catálogos | ✅ |
| **v5** | **Inteligencia artificial**: detección automática de **valores atípicos** por sitio (estadística robusta) e **interpretación del informe** redactada por IA (Claude) y revisada por un supervisor | ✅ |
| **v5** | **Panel de inicio** con indicadores, alertas de calidad y **tendencias** por sitio y parámetro | ✅ |

### Flujo de una muestra

```
Recibida ─► En análisis ─► Resultados ingresados ─► Aprobada ─► Reportada (informe PDF)
                                  ▲            └──► Rechazada
                                  └── corrección del analista (versión nueva, con motivo) ──┘
```

### Roles

| Rol | Puede |
|---|---|
| **Analista** | Registrar investigadores, sitios y muestras, firmar la cadena de custodia, asignar análisis, ingresar y corregir resultados |
| **Supervisor** | Todo lo anterior + aprobar/rechazar resultados, redactar la interpretación (IA) y emitir informes PDF |
| **Administrador** | Todo lo anterior + administrar usuarios, el catálogo de parámetros y rangos, los equipos (calibración) y las ubicaciones |

---

## Aporte de cada integrante

Basado en el historial de commits del repositorio (`git log`, `git shortlog -sn`). Cada integrante trabaja en su propia rama (`Cardenas_Nicolas`, `Solis_Cristian`, `Murua_Claudio`) y se integra a `main` por pull request.

| Integrante | Rol Scrum | Aporte principal |
|---|---|---|
| **Nicolás Cárdenas** | Scrum Master | Estructura del repositorio y documentación de Fase 1 y 2 (presentación, informe, Carta Gantt). **Backend:** catálogo de análisis (CRUD), análisis por muestra y **resultados con validación de rango** (RF02/RF03), **aprobaciones** con avance de estado (RF04), historial de informes emitidos (RF05) con su pantalla. Base compartida en la nube (Aiven) y documentación de endpoints |
| **Cristian Solís** | Equipo de desarrollo | Base del proyecto (Spring Boot + JavaFX). **Muestras** (RF01): entidades JPA, endpoints, ventanas y registro de muestra. Endpoints de detalle, **pantalla de aprobación** (RF04) e historial de estados. **Exportación a PDF** (RF05). **v4:** dominio ambiental (investigadores, cadena de custodia) y seguridad por rol (RF06) |
| **Claudio Murúa** | Product Owner | **Sistema de usuarios** (RF06): login, registro, roles y reseteo de contraseña (backend + cliente) y su documentación. **v5 (respuesta a la retroalimentación de Fase 2):** trazabilidad del dato (versiones, equipos con calibración, línea de tiempo), sitios georreferenciados y mapa, catálogos desplegables, detección de anomalías, interpretación con IA, panel de inicio, informe PDF ampliado y datos de demostración |

> Tabla a validar por el equipo antes de la entrega: cada integrante debe confirmar su fila y, en la presentación, mostrar en vivo su propia parte.

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

En una base de datos nueva, el backend crea el usuario administrador **`admin` / `admin123`**. Si usan una base que ya existía (por ejemplo Aiven), primero ejecuten **en orden** las migraciones `lims-backend/db-migrations/v4-dominio-ambiental.sql` y `v5-trazabilidad-sitios-ia.sql`. Desde ahí se crean los Supervisores y Analistas en la pantalla *Usuarios* (cambien esa contraseña al entrar).

**Para una demo con datos** (tendencias, mapa y valores atípicos necesitan historial), carguen en la BD local `lims-backend/db-migrations/datos-demo-v5.sql`: crea 8 meses de muestras ambientales y los usuarios `supervisor.demo` / `super123` y `analista.demo` / `analista123`. El paso a paso de la presentación está en **[GUION-DEMO.md](Evidencia%20Proyecto/lims/GUION-DEMO.md)**.

**IA (opcional):** si el backend arranca con la variable de entorno `ANTHROPIC_API_KEY`, el borrador de interpretación del informe lo redacta Claude; sin ella, se genera por reglas y todo lo demás funciona igual.

➡️ La guía completa —configuración, base en la nube, endpoints de la API, datos de prueba con `test.http` y solución de problemas— está en **[Evidencia Proyecto/lims/README.md](Evidencia%20Proyecto/lims/README.md)**.

---

## Pantallas principales

| Pantalla | Descripción |
|---|---|
| Inicio de sesión | Login, registro (como Analista) y "¿Olvidaste tu contraseña?" |
| **Inicio** | Indicadores, **alertas de calidad** (atípicos, calibraciones, rechazos, conservación) y **tendencia** de un parámetro por sitio |
| Listado de muestras | Tabla con estados en color y buscador en vivo |
| Registro de muestra | Investigador, **sitio del catálogo**, fecha de toma, **condición y ubicación desplegables** y firma de recepción; genera el código único |
| Detalle de muestra | Datos, análisis, ingreso y **corrección** de resultados, **versiones**, **trazabilidad completa**, historial, **cadena de custodia**, **interpretación (IA)** e informe PDF |
| **Sitios y mapa** | Mapa interactivo de los sitios de muestreo, coloreados según sus resultados |
| **Equipos y almacenamiento** | Equipos con estado de calibración y ubicaciones de almacenamiento |
| Investigadores | Quienes encargan los muestreos y reciben los informes |
| Parámetros y rangos | Tabla de rangos normales por parámetro (el Administrador la mantiene) |
| Aprobaciones | Resultados pendientes con panel de revisión (Supervisor / Administrador) |
| Usuarios | Crear usuarios con rol, resetear contraseñas, activar/desactivar (Administrador) |

---

## Próximos pasos

- Pruebas unitarias JUnit de los servicios (detección de anomalías, corrección de resultados, aprobaciones).
- Validar con la investigadora los rangos reales de los parámetros de algas y registrar su fuente.
- Código QR en la etiqueta de la muestra y en el informe, que lleve a su trazabilidad.
- Instalador (`.exe`), video demo, informe final y presentación.
- HTTPS para una instalación en producción (RNF04).
