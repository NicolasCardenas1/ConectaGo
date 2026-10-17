# LIMS - Sistema de Gestión de Información de Laboratorio

Proyecto Capstone (DuocUC) — Cristian Solís, Claudio Murúa, Nicolás Cárdenas.

Aplicación de escritorio en JavaFX conectada a un backend Spring Boot y una base de datos MySQL, para la gestión de muestras, resultados e informes de un laboratorio de **investigación ambiental**.

### Dominio (v4)

- **Usuarios del LIMS:** el personal del laboratorio (Analista, Supervisor, Administrador).
- **Investigador:** quien toma muestras en terreno (por ejemplo, **algas**), encarga los análisis y **recibe el informe**. Se registra como entidad (`investigadores`), no inicia sesión.
- **Valor central:** conservar y **trazar** muestras durante largos periodos sin papel: sitio y fecha de muestreo, condición, ubicación de almacenamiento, "conservar hasta" y **cadena de custodia firmada**.
- **No es** un sistema clínico: no hay pacientes ni perfiles bioquímicos.
- Alcance: versión de 12 semanas (JavaFX + Spring Boot + MySQL) de una propuesta original de 8 meses.

## Estructura del repositorio

```
lims/
├── README.md                # esta guía
├── README-aiven.md          # conexión a la base de datos compartida en la nube
├── lims-backend/            # API REST (Spring Boot + JPA + Spring Security + MySQL)
│   ├── docker-compose.yml   # levanta MySQL en un contenedor
│   ├── db-init/             # 01-schema.sql: esquema completo (solo corre con un volumen de BD nuevo)
│   ├── db-migrations/       # scripts para actualizar BD que ya existen (ej. v4-dominio-ambiental.sql)
│   ├── test.http            # flujo completo de pruebas de la API (cliente HTTP de IntelliJ)
│   ├── reportes/            # informes PDF generados (ignorado por git)
│   └── src/main/java/com/duoc/lims/limsbackend/
│       ├── config/          # SecurityConfig (permisos por rol), DatosInicialesConfig (admin inicial)
│       ├── security/        # sesiones con token (SesionService, TokenAuthFilter, UsuarioAutenticado)
│       ├── controller/      # endpoints REST
│       ├── service/         # reglas de negocio (estados, aprobaciones, custodia, PDF...)
│       ├── repository/      # acceso a datos (Spring Data JPA)
│       ├── model/           # entidades JPA, enums y converters
│       └── dto/             # objetos de entrada/salida de la API
└── lims-client/             # aplicación de escritorio (JavaFX)
    └── src/main/
        ├── java/com/duoc/lims/limsclient/
        │   ├── Navigator.java   # navegación y permisos de pantalla por rol
        │   ├── controller/      # un controlador por pantalla
        │   ├── model/           # modelos (records de Java 21)
        │   ├── service/         # ApiClient: única conexión con el backend
        │   └── ui/              # Badges (etiquetas de color) y Formularios (diálogos)
        └── resources/com/duoc/lims/limsclient/   # vistas .fxml y styles.css
```

### Modelo de datos (tablas principales)

| Tabla | Para qué sirve |
|---|---|
| `centros` | Laboratorio que usa el sistema (MVP de un solo centro) |
| `roles`, `usuarios` | Personal del laboratorio y su rol (Administrador, Supervisor, Analista) |
| `investigadores` | Quien encarga el muestreo y recibe el informe (no inicia sesión) |
| `muestras` | Muestra con código único, tipo, sitio y fecha de toma, almacenamiento, *conservar hasta* y estado |
| `custodia_eventos` | Cadena de custodia: entregas, traslados y retiros firmados por quien recibe |
| `muestra_historial_estado` | Cada cambio de estado de la muestra (quién, cuándo, por qué) |
| `analisis_catalogo` | Parámetros con su **rango normal** (mínimo, máximo, unidad, método/fuente) |
| `muestra_analisis` | Parámetros solicitados para cada muestra |
| `resultados` | Valor medido, si está dentro de rango y quién lo ingresó |
| `aprobaciones` | Evaluación del supervisor (aprobado/rechazado y motivo) |
| `reportes` | Cada informe PDF emitido (con versión) |

## Requisitos previos

Instalar antes de clonar el proyecto:

- **JDK 21** (no JRE, no Java 8) — el proyecto compila con `release 21`. Se recomienda **Eclipse Temurin 21** (https://adoptium.net/). Verifica con:
  ```
  java -version
  javac -version
  ```
  Si `javac` no existe o la versión no es 21, instala Temurin 21 y configura `JAVA_HOME` y el `Path` del sistema apuntando a esa instalación (en Windows, hazlo desde "Variables de entorno del sistema", no con `setx` en PowerShell, porque `setx` puede truncar o sobrescribir el `Path` existente).
- **Docker Desktop** (para levantar MySQL en un contenedor). Debe quedar con el motor ("Engine") corriendo antes de usar `docker-compose`.
- **Git**.
- **IntelliJ IDEA** (Community o Ultimate) — recomendado, es lo que usa el equipo.
- No es necesario instalar Maven aparte: ambos proyectos incluyen Maven Wrapper (`mvnw` / `mvnw.cmd`).

## 1. Clonar el repositorio

```
git clone https://github.com/NicolasCardenas1/ConectaGo.git
cd ConectaGo
```

> El proyecto LIMS vive dentro de este repo, en la carpeta `Evidencia Proyecto/lims/` (no en la raíz). Los comandos de las secciones siguientes (`cd lims-backend`, `cd lims-client`, etc.) se ejecutan siempre desde ahí, por ejemplo:
> ```
> cd "Evidencia Proyecto/lims"
> ```

## 2. Levantar la base de datos (Docker)

La base de datos corre en un contenedor MySQL definido en `lims-backend/docker-compose.yml` (ya está en el repo, no hay que crearlo):

```yaml
services:
  db:
    image: mysql:8.4
    container_name: lims-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_DATABASE: lims_db
      MYSQL_USER: lims_user
      MYSQL_PASSWORD: lims_pass
    ports:
      - "3307:3306"
    volumes:
      - lims-data:/var/lib/mysql
      - ./db-init:/docker-entrypoint-initdb.d
volumes:
  lims-data:
```

Para levantarla, cada integrante solo necesita (con Docker Desktop abierto y el motor corriendo):

```
cd lims-backend
docker-compose up -d
```

Esto crea el contenedor `lims-mysql`, expuesto en el puerto **3307** del host (para no chocar con un MySQL local que ya tengas en el 3306 por defecto), con usuario `lims_user` / contraseña `lims_pass` sobre el esquema `lims_db`.

Verifica que quedó arriba con:

```
docker ps
```

> ⚠️ La contraseña de root (`rootpassword`) queda en texto plano en este archivo versionado en git. Es aceptable para un entorno de desarrollo local del equipo, pero no la reutilicen en nada expuesto a internet.

Notas importantes para tus compañeros:
- Los scripts dentro de `lims-backend/db-init/` (como `01-schema.sql`) **solo se ejecutan la primera vez** que se crea el volumen `lims-data`. Si alguien ya había levantado el contenedor antes de un cambio de esquema, hay que recrearlo desde cero:
  ```
  docker-compose down -v
  docker-compose up -d
  ```
  (`-v` borra también el volumen `lims-data`, no solo el contenedor).
- **Usuario inicial:** con la seguridad activa (RF06), solo un Administrador puede crear usuarios con rol. Por eso, **si la BD no tiene ningún usuario**, el backend crea al arrancar el Administrador **`admin` / `admin123`** (cámbienle la contraseña desde la pantalla *Usuarios*). Desde la pantalla pública **"Crear usuario"** cualquiera puede registrarse, pero **siempre queda como Analista**. Como alternativa para pruebas vía SQL directo, puedes insertar un usuario manualmente:

  ```
  docker exec -it lims-mysql mysql -u lims_user -plims_pass lims_db
  ```
  Y dentro del prompt `mysql>` ejecuta:
  ```sql
  INSERT INTO usuarios (id_centro, nombre, apellido, email, username, password_hash, id_rol)
  VALUES (1, 'Tu Nombre', 'Tu Apellido', 'tu_correo@lims.cl', 'tu_usuario', 'pendiente-hasta-login', 3);
  ```
  > ⚠️ Un usuario insertado así por SQL directo **no podrá iniciar sesión**, porque `password_hash` no queda como un hash BCrypt válido — solo sirve como llave foránea para pruebas. Para un usuario que sí pueda loguearse, créalo desde la pantalla *Usuarios* (como admin) o con *Crear usuario* del login.

  Donde:
  - `id_centro = 1` → "Laboratorio Central" (precargado en la tabla `centros`).
  - `id_rol`: `1 = Administrador`, `2 = Supervisor`, `3 = Analista` (precargados en la tabla `roles`).

  Si tienes dudas sobre los valores disponibles, revísalos con:
  ```sql
  SELECT * FROM centros;
  SELECT * FROM roles;
  ```
- **Si tu base de datos ya existía** (no la recreaste desde cero), aplícale los cambios de esquema con los scripts de `lims-backend/db-migrations/` — ver la sección **Migración a la versión 4**. Si es muy antigua y le falta la columna `requiere_reset_password`, agrégala primero:
  ```sql
  ALTER TABLE usuarios ADD COLUMN requiere_reset_password BOOLEAN NOT NULL DEFAULT FALSE;
  ```

## 3. Configurar la conexión del backend

`lims-backend/src/main/resources/application.properties` ya está en el repo con esta configuración (nadie necesita cambiar nada si usa el mismo `docker-compose.yml` de arriba):

```properties
spring.datasource.url=jdbc:mysql://localhost:3307/lims_db
spring.datasource.username=lims_user
spring.datasource.password=lims_pass

spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

`ddl-auto=none` significa que Hibernate **no** crea ni modifica tablas automáticamente — el esquema lo define únicamente `db-init/01-schema.sql`. Si alguien agrega un campo a una entidad, hay que reflejarlo también ahí (y, en la BD ya creada de cada uno, con un `ALTER TABLE` manual).

## 4. Levantar el backend

```
cd lims-backend
.\mvnw.cmd clean compile
.\mvnw.cmd spring-boot:run
```

Verificación rápida: en el log debe aparecer `Started LimsBackendApplication` (y, con una BD nueva, el aviso de que se creó el usuario `admin`). Como la API exige sesión, un `GET http://localhost:8080/api/muestras` sin token responde **401**: eso confirma que el backend está arriba y la seguridad activa. Para probar con datos, usa `test.http` (abajo).

### Datos de prueba con `test.http`

`lims-backend/test.http` contiene el **flujo completo** numerado (ejecútalo en orden sobre una BD recién creada con `docker-compose down -v` + `up -d`):

| Paso | Qué hace |
|---|---|
| 0 | Login del administrador inicial `admin` / `admin123` (el token se guarda solo en `{{tokenAdmin}}`) |
| 1a / 1b | El admin crea un **Supervisor** (`cristian` / `1234`); y por registro público se crea `ana` / `1234`, que queda como **Analista** aunque pida rol Administrador |
| 2a / 2b | Login del supervisor y de la analista (`{{tokenSup}}`, `{{tokenAna}}`) |
| S1 – S5 | Pruebas de seguridad que **deben fallar**: sin token (401), analista listando usuarios, creando análisis, aprobando o emitiendo un informe (403) |
| 3 – 6b | El admin crea parámetros del catálogo; la analista registra al **investigador**, la muestra (con almacenamiento y **firma de recepción**), firma un **traslado** en la cadena de custodia, asigna análisis e ingresa resultados |
| 7 – 8c | El supervisor aprueba (la muestra pasa a *Aprobada*); aprobar dos veces da 400 |
| 9 – 10 | Detalle con historial y custodia, y emisión del informe PDF dirigido al investigador (la muestra pasa a *Reportada*) |
| 11 – 15 | Administración de usuarios: listar, reset de contraseña, desactivar y logout |

## 5. Levantar el cliente JavaFX

En otra terminal:

```
cd lims-client
.\mvnw.cmd clean compile
.\mvnw.cmd javafx:run
```

Debería abrirse la ventana **"LIMS - Inicio de Sesión"** (pantalla de login). Desde ahí puedes:
- Iniciar sesión con un usuario existente.
- Usar **"Crear usuario"** para registrarte.
- Usar **"¿Olvidaste tu contraseña?"** para enviar una solicitud de reseteo.

Al iniciar sesión, la ventana se agranda y aparece el **marco principal**: una barra lateral fija (con el nombre y rol del usuario y el botón **Cerrar sesión**) y el área de contenido a la derecha.

### Pantallas del cliente

| Pantalla | Cómo se llega | Qué permite |
|---|---|---|
| Inicio de sesión / Crear usuario / Recuperar contraseña | Al abrir la app | Autenticarse contra el backend (RF06). *Crear usuario* siempre registra Analistas |
| **Listado de muestras** (mockup 11.2) | Barra lateral → *Muestras* | Tabla con investigador, sitio y estado en etiquetas de color, **buscador en vivo** por código/investigador/sitio/tipo, doble clic para abrir el detalle |
| **Registro de muestra** (mockup 11.3) | *+ Nueva muestra* | Investigador (con alta rápida), tipo ambiental, sitio y fecha de toma, condición, ubicación, *conservar hasta* y **firma de recepción** obligatoria |
| **Detalle de muestra** | Doble clic o *Ver detalle* en el listado | Investigador, muestreo y almacenamiento; pestañas **Análisis solicitados** (asignar e ingresar resultados), **Historial de estados** y **Cadena de custodia** (firmar traslados, retiros y devoluciones) |
| **Investigadores** | Barra lateral → *Investigadores* | Registrar, editar y activar/desactivar investigadores |
| **Parámetros y rangos** | Barra lateral → *Parámetros y rangos* | **Tabla explícita de rangos normales** por parámetro (mínimo, máximo, unidad, método/fuente). Solo el Administrador la modifica |
| **Ingreso de resultado** (mockup 11.4) | Detalle → seleccionar análisis → *Ingresar resultado* | Diálogo que avisa **en vivo** si el valor está fuera de rango y exige observaciones (RF02/RF03). Acepta coma decimal (`7,2`) |
| **Aprobación de resultados** (mockup 11.5) | Barra lateral → *Aprobaciones* | Tabla de resultados pendientes + panel de revisión con botones **Aprobar** / **Rechazar** (comentario obligatorio al rechazar) (RF04) |
| **Informe PDF** (mockup 11.6) | Detalle de una muestra Aprobada → *Generar informe PDF* | Emite el informe y permite **ver la vista previa** (se abre en el visor de PDF del sistema) o **guardarlo como…** (RF05) |
| **Administración de usuarios** | Barra lateral → *Usuarios* (solo Administrador) | Crear usuarios con rol, resetear contraseñas (las solicitudes de "¿Olvidaste tu contraseña?" aparecen marcadas) y activar/desactivar cuentas (RF06) |

**La barra lateral se adapta al rol (RF06):**

| Opción | Analista | Supervisor | Administrador |
|---|:-:|:-:|:-:|
| Muestras (listado, registro, detalle, ingreso de resultados, custodia) | ✅ | ✅ | ✅ |
| Investigadores | ✅ | ✅ | ✅ |
| Parámetros y rangos (ver) / (editar) | ✅ / — | ✅ / — | ✅ / ✅ |
| Aprobaciones | — | ✅ | ✅ |
| Botón *Generar informe PDF* en el detalle | — | ✅ | ✅ |
| Usuarios | — | — | ✅ |

Las opciones *Reportes y Configuración* aparecen deshabilitadas ("próximamente").

### Estructura del cliente (lo más importante)

- `Navigator.java` — toda la navegación. Login, Crear usuario y Recuperar contraseña ocupan la ventana completa; el resto de pantallas se cargan **dentro** del marco (`main-layout.fxml` + `MainLayoutController`).
- `service/ApiClient.java` — único punto de contacto con el backend (HTTP + JSON).
- `model/` — modelos del cliente. Los nuevos son `record` de Java 21 (`MuestraDetalle`, `AnalisisCatalogo`, `ResultadoPendiente`, etc.): se accede con `nombre()` en vez de `getNombre()`.
- `ui/Badges.java` — etiquetas de color para estados, roles y controles (se usan en las tablas con `col.setCellFactory(c -> Badges.celda())`).
- `ui/Formularios.java` — arma los diálogos con formulario (investigador, parámetro, custodia, usuario) con el estilo de la app.
- `styles.css` — tema completo de la app (barra lateral, tablas, botones, etiquetas). Los estilos del login están al inicio del archivo y el tema de la app al final.

## 6. Módulos y endpoints del backend (API REST)

El backend expone los siguientes módulos, todos bajo `http://localhost:8080`.

### Muestras (RF01)
- `GET  /api/muestras` — lista todas las muestras.
- `GET  /api/muestras/{id}` — **detalle** de una muestra: sus datos, cada análisis solicitado con su resultado y su última aprobación, y el **historial de estados**.
- `POST /api/muestras` — registra una muestra (genera código único automático y deja el primer registro del historial: *Recepción de la muestra*).

### Informe de resultados en PDF (RF05)
- `POST /api/muestras/{id}/reporte` — emite el **informe PDF** de la muestra para el investigador y lo devuelve (`application/pdf`). Solo **Supervisor/Administrador**; el emisor es el usuario de la sesión.
  - Solo para muestras **Aprobadas** (o ya *Reportadas*: se emite una nueva versión `-v2`, `-v3`…).
  - Contenido: datos del laboratorio, N° de informe con versión y fecha, **investigador solicitante**, datos de la muestra (sitio y fecha de toma, almacenamiento, *conservar hasta*), tabla de resultados (método, valor, unidad, rango, control *En rango / Fuera de rango*, analista y quién validó), observaciones, **cadena de custodia**, validación/emisión y nota de alcance tipo ISO 17025.
  - Cada emisión queda registrada en la tabla `reportes` y el archivo se guarda en `lims-backend/reportes/` (configurable con la propiedad `lims.reportes.dir`; la carpeta está en el `.gitignore`).
  - La muestra pasa a estado **Reportada** (queda en el historial).
  - Librería: **OpenPDF 1.3.43** (`com.github.librepdf:openpdf`).

### Investigadores (v4)
- `GET   /api/investigadores?activos=true|false` — lista (ordenada por apellido).
- `GET   /api/investigadores/{id}` · `POST /api/investigadores` · `PUT /api/investigadores/{id}` — campos: `nombre`, `apellido` (obligatorios), `institucion`, `email`, `telefono`, `proyecto`.
- `PATCH /api/investigadores/{id}/estado?activo=` — un investigador inactivo no puede asociarse a muestras nuevas.

### Muestreo, almacenamiento y cadena de custodia (v4)
- `POST /api/muestras` ahora recibe además: `idInvestigador` (obligatorio), `procedencia` (= **sitio de muestreo**, obligatorio), `fechaToma`, `condicionRecepcion`, `ubicacionAlmacenamiento`, `fechaVencimiento` (*conservar hasta*), `entregadaPor` y `firmaRecepcion`. Con `firmaRecepcion: true` se crea el primer evento de custodia (*Recepción en laboratorio*) firmado por el usuario de la sesión.
- `GET  /api/muestras/{id}/custodia` — eventos de custodia de la muestra.
- `POST /api/muestras/{id}/custodia` — firma un evento: `tipoEvento` (*Recepción en laboratorio, Traslado, Retiro para análisis, Devolución a almacenamiento, Devolución al investigador*), `entregadoPor`, `ubicacion`, `condicion`, `observaciones` y `confirmado: true` (la firma; sin ella responde 400). Quien recibe es el usuario de la sesión. La `ubicacion` declarada pasa a ser la ubicación actual de la muestra.
- `GET /api/muestras/{id}` incluye `custodia`, y el informe PDF incluye las secciones **Investigador solicitante** y **Cadena de custodia**.

### Catálogo de parámetros y rangos normales (RF03)
Es la "tabla de rangos" contra la que se valida cada resultado; en el cliente se ve en **Parámetros y rangos**.
- `GET   /api/analisis` · `GET /api/analisis/{id}` — cualquier usuario con sesión.
- `POST  /api/analisis` — crea un parámetro (valida que mínimo ≤ máximo). Solo **Administrador**.
- `PUT   /api/analisis/{id}` — edita un parámetro. Solo **Administrador**.
- `PATCH /api/analisis/{id}/estado?activo=false` — activa/desactiva (baja lógica; un parámetro inactivo no se puede asignar a muestras nuevas). Solo **Administrador**.

> Los rangos deben provenir de una fuente validada (norma, literatura o protocolo del laboratorio); registren la fuente en `metodoReferencia`. Los rangos de `test.http` son solo de prueba.

### Análisis solicitados por muestra (RF02)
- `POST /api/muestra-analisis` — asigna un análisis a una muestra
  (evita duplicados y análisis inactivos).
- `GET  /api/muestra-analisis?idMuestra={id}` — lista los análisis de una muestra.

### Resultados (RF02 / RF03)
- `POST /api/resultados` — ingresa el resultado de un análisis solicitado.
  El backend calcula automáticamente si el valor está dentro del rango del
  análisis (`dentroRango`). Si queda fuera de rango, exige justificación en
  `observaciones` (ISO 17025). Al ingresarse, el análisis pasa a "Completado"
  y la muestra avanza a "En analisis" (o a "Resultados ingresados" si ya todos
  sus análisis tienen resultado).
- `GET  /api/resultados` — lista todos los resultados.
- `GET  /api/resultados?pendientes=true` — solo los resultados que **aún no tienen evaluación** del supervisor (alimenta la pantalla de Aprobaciones).

### Aprobación de resultados (RF04)
- `POST /api/aprobaciones` — aprueba o rechaza un resultado
  (`estadoAprobacion`: "Aprobado" / "Rechazado"). Reglas que valida el backend:
  - Solo puede evaluar un usuario con rol **Supervisor** o **Administrador**.
  - **Nadie puede evaluar un resultado que él mismo ingresó** (segregación de funciones, ISO 17025).
  - Un resultado se evalúa **una sola vez** (no se permiten aprobaciones duplicadas).
  - Un rechazo exige `comentario`.

  Al evaluar, la muestra pasa a "Rechazada" ante un rechazo, o a "Aprobada"
  cuando todos sus análisis quedan aprobados (si aún faltan, conserva su estado).

### Flujo de estados de la muestra e historial (trazabilidad ISO 17025)

```
Recibida ──(1er resultado)──► En analisis ──(todos con resultado)──► Resultados ingresados ──(todos aprobados)──► Aprobada ──(informe PDF)──► Reportada
                                                                                         └──(un rechazo)──────► Rechazada
```

Todo cambio de estado pasa por `EstadoMuestraService`, que actualiza la muestra **y** registra una fila en
`muestra_historial_estado` con el estado anterior, el nuevo, el usuario que lo provocó, la fecha y el motivo.
El historial se consulta en `GET /api/muestras/{id}` y se ve en la pestaña *Historial de estados* del cliente.

> Las muestras creadas antes de este cambio no tienen historial (la tabla ya existía en el esquema, solo que nadie la llenaba).
>
> **Pendiente (decisión del equipo):** qué hacer después de un rechazo. Hoy un rechazo deja la muestra en *Rechazada* de forma definitiva; falta definir el flujo de reingreso/corrección del resultado.

### Usuarios y autenticación (RF06)

**Sesión con token.** `POST /api/usuarios/login` devuelve `{ token, expira, usuario }`. El cliente envía el token en **todas** las peticiones con el encabezado `Authorization: Bearer <token>`. Sin token (o vencido) la API responde **401**; con token pero sin el rol necesario, **403**. Las sesiones duran 8 horas (propiedad `lims.sesion.horas`) y se guardan en memoria: si el backend se reinicia, hay que volver a iniciar sesión.

**El usuario sale del token**: al registrar muestras, ingresar resultados, aprobar o emitir informes, el backend usa el usuario de la sesión e ignora cualquier `idUsuario…` que envíe el cliente (nadie puede hacerse pasar por otro).

Públicos (sin token):
- `POST /api/usuarios/login` — autentica (`username` + `password`). Actualiza `fecha_ultimo_login`.
- `POST /api/usuarios/registro` — registro público; la cuenta **siempre** queda como **Analista**.
- `POST /api/usuarios/solicitar-reset` — "olvidé mi contraseña": marca al usuario con `requiereResetPassword = true`.

Con sesión:
- `POST /api/usuarios/logout` — cierra la sesión (el token deja de servir).

Solo **Administrador**:
- `GET   /api/usuarios` — lista los usuarios (nunca expone `password_hash`).
- `POST  /api/usuarios` — crea un usuario con cualquier rol. Body: `idCentro`, `nombre`, `apellido`, `email`, `username`, `password`, `idRol` (1 Administrador, 2 Supervisor, 3 Analista).
- `GET   /api/usuarios/solicitudes-reset` — usuarios con solicitud de reseteo pendiente.
- `PUT   /api/usuarios/{id}/resetear-password` — nueva contraseña (Body: `nuevaPassword`); cierra las sesiones abiertas de ese usuario.
- `PATCH /api/usuarios/{id}/estado?activo=true|false` — activa/desactiva una cuenta (no puedes desactivarte a ti mismo); al desactivar se cierran sus sesiones.

**Permisos por rol (definidos en `SecurityConfig`):**

| Acción | Analista | Supervisor | Administrador |
|---|:-:|:-:|:-:|
| Ver muestras, detalle, catálogo y resultados | ✅ | ✅ | ✅ |
| Registrar muestra, asignar análisis, ingresar resultado | ✅ | ✅ | ✅ |
| Aprobar / rechazar resultados (`POST /api/aprobaciones`) | ❌ | ✅ | ✅ |
| Emitir informe PDF (`POST /api/muestras/{id}/reporte`) | ❌ | ✅ | ✅ |
| Crear / editar / desactivar análisis del catálogo | ❌ | ❌ | ✅ |
| Administrar usuarios y contraseñas | ❌ | ❌ | ✅ |

## Migración a la versión 4 (dominio ambiental)

Una **BD nueva** (`docker-compose down -v` + `up -d`) ya trae todo. Para una **BD existente** (Aiven, o un Docker que no quieran borrar) ejecuten **una vez** `lims-backend/db-migrations/v4-dominio-ambiental.sql`:

```powershell
cd lims-backend
Get-Content db-migrations\v4-dominio-ambiental.sql | docker exec -i lims-mysql mysql -u lims_user -plims_pass lims_db
```

El script crea `investigadores` y `custodia_eventos`, agrega `muestras.id_investigador` y convierte los tipos de muestra antiguos (*Agua potable/residual → Agua dulce*, *Suelo → Sedimento*, *Sangre/Alimento → Otro*). Sin esta migración el backend falla al leer muestras (`Unknown column 'id_investigador'` o *Tipo de muestra no válido*).

Después, en la pantalla **Parámetros y rangos** (como admin), **desactiven los análisis clínicos de prueba** (hemograma, glucosa, colesterol…) y carguen los parámetros ambientales con su fuente.

## Problemas comunes

- **`No compiler is provided in this environment. Perhaps you are running on a JRE rather than a JDK?`**: tienes instalado un JRE (o Java 8) en vez de un JDK 21. Instala Eclipse Temurin JDK 21 y verifica con `javac -version`.
- **`"mvnw" / "powershell" no se reconoce como un comando interno o externo` después de tocar `JAVA_HOME`/`Path`**: normalmente indica que el `Path` del sistema quedó corrupto (por ejemplo, sobrescrito por un `setx` mal usado, perdiendo entradas como `C:\Windows\System32`). Revísalo con:
  ```powershell
  [Environment]::GetEnvironmentVariable('Path','Machine')
  ```
  y reconstrúyelo agregando de vuelta las rutas base de Windows (`C:\Windows\system32`, `C:\Windows`, `C:\Windows\System32\Wbem`, `C:\Windows\System32\WindowsPowerShell\v1.0\`, `C:\Windows\System32\OpenSSH\`) más la ruta `bin` del JDK 21 instalado, usando:
  ```powershell
  [Environment]::SetEnvironmentVariable('Path', '<lista completa y correcta>', 'Machine')
  ```
  Cierra y vuelve a abrir la terminal después de este cambio.
- **`failed to connect to the docker API` / `No such container: lims-mysql`**: Docker Desktop no está abierto (ábrelo y espera a que el motor quede "running"), o no te ubicaste dentro de `lims-backend` antes de correr `docker-compose up -d`.
- **`ERROR 1364: Field 'id_centro' doesn't have a default value` (u otro campo obligatorio) al insertar en `usuarios`**: la tabla real tiene columnas por llave foránea (`id_centro`, `id_rol`) que no estaban en un ejemplo anterior de este README. Usa el `INSERT` completo de la sección 2 más arriba. Si necesitas confirmar las columnas reales, ejecuta `DESCRIBE usuarios;` dentro del cliente MySQL del contenedor.
- **`ClassNotFoundException` al hacer `javafx:run`**: revisar que en `lims-client/pom.xml`, dentro del plugin `javafx-maven-plugin`, el `<mainClass>` esté como clase simple (`com.duoc.lims.limsclient.HelloApplication`), **sin** prefijo de módulo (`module/Class`). El proyecto no usa `module-info.java`.
- **`401` "Sesión no válida o expirada"**: la petición no lleva token, el token venció (8 h) o el backend se reinicia (las sesiones viven en memoria). En el cliente basta con volver a iniciar sesión (la app vuelve al login sola). En `test.http`, ejecuta de nuevo los logins (pasos 0, 2a y 2b).
- **`403` "No tienes permisos para realizar esta acción"**: el usuario tiene sesión pero su rol no permite esa acción (ver la tabla de permisos en la sección 6).
- **No puedo entrar como administrador en una BD que ya tenía usuarios (ej. Aiven)**: el admin inicial `admin` / `admin123` solo se crea si la tabla `usuarios` está **vacía**. Si ya hay usuarios, asígnale el rol a uno existente por SQL: `UPDATE usuarios SET id_rol = 1 WHERE username = 'tu_usuario';`
- **`404 Not Found` en un endpoint que debería existir (ej. `/api/usuarios`)**: primero confirma con `mvnw clean compile` que el archivo del controlador realmente tiene contenido y compiló (`target\classes\...\NombreController.class` debe existir). Si compiló bien, revisa que no haya **dos procesos Java corriendo a la vez** (`Get-Process java`) — un proceso viejo puede seguir "pegado" al puerto 8080 sirviendo código antiguo mientras el nuevo falla en silencio al no poder tomar el puerto.
- **`java.net.ConnectException` en el cliente JavaFX al crear un usuario, iniciar sesión, etc.**: el backend no está corriendo (o se cayó) en `localhost:8080`. Verifica con `Invoke-RestMethod -Uri "http://localhost:8080/api/usuarios" -Method Get` antes de usar el cliente.
- **Error `Column 'fecha_recepcion' cannot be null` al crear una muestra**: la entidad `Muestra` debe tener el campo `fechaRecepcion` anotado con `@CreationTimestamp` (Hibernate), no depender solo del `DEFAULT CURRENT_TIMESTAMP` de MySQL.
- **`Unknown column 'id_investigador'`, `Table 'lims_db.investigadores' doesn't exist` o `Tipo de muestra no válido: Agua potable`**: tu BD es anterior a la versión 4. Recréala (`docker-compose down -v` + `up -d`) o ejecuta `db-migrations/v4-dominio-ambiental.sql` (sección *Migración a la versión 4*).
- **400 "idInvestigador es obligatorio" / "el sitio de muestreo es obligatorio" al crear una muestra**: desde la v4 toda muestra debe tener investigador y sitio de muestreo. Registra primero al investigador (pantalla *Investigadores* o `POST /api/investigadores`).
- **400 "Debes confirmar (firmar)..."**: los eventos de custodia exigen `confirmado: true` (en el cliente, marcar la casilla de confirmación).
- **`Unknown column 'requiere_reset_password'` (error 500 en casi todos los endpoints)**: tu base local en Docker se creó con un esquema anterior. Recréala con `docker-compose down -v` y `docker-compose up -d` (borra los datos locales) o aplica el `ALTER TABLE` de la sección 2.
- **`Communications link failure` al arrancar el backend / `Connection refused` en el puerto 8080**: el backend no logró conectarse a MySQL y se cerró. Si en el log dice `No active profile set`, está usando la BD **local**: abre Docker Desktop y ejecuta `docker-compose up -d`. Si querías usar Aiven, arranca con el perfil (ver sección 7).
- **Aiven: `nslookup` responde `Non-existent domain`**: el servicio de Aiven está **apagado** (el plan gratuito se apaga por inactividad y su dominio desaparece). Quien administra la cuenta debe entrar a console.aiven.io y presionar *Power on*. Mientras tanto se puede trabajar con la BD local.
- **`unnamed classes are a preview feature` al compilar**: un método quedó pegado **fuera** de la clase (después de la última `}` del archivo). Muévelo dentro de la clase.
- **403 al aprobar o 400 "No puedes evaluar un resultado que tú mismo ingresaste"**: no es un error, son las reglas de RF04/RF06. Para probar el flujo completo se necesitan dos usuarios: un **Analista** que ingresa el resultado y un **Supervisor** distinto que lo aprueba (ver `test.http`).
- **Errores de compilación por paquete no encontrado**: confirmar que el paquete base sea `com.duoc.lims.limsbackend` (backend) y `com.duoc.lims.limsclient` (cliente) en todas las clases — un error de tipeo aquí genera decenas de errores en cadena.
- **VS Code marca en rojo `getNombre()`, `setApellido()`, etc. como "cannot find symbol" en una entidad (`Usuario`, `Rol`, `Centro`)**: es un falso positivo del Language Server de Java, que no reconoce los getters/setters generados por Lombok (`@Getter`/`@Setter`). No afecta la compilación real con Maven. Se puede limpiar con `Ctrl+Shift+P` → "Java: Clean Java Language Server Workspace", o instalando la extensión "Lombok Annotations Support for VS Code".

## Estado de los requisitos funcionales

| Requisito | Backend | Cliente JavaFX |
|---|---|---|
| **RF01** Registro de muestras | ✅ Con investigador, sitio y fecha de toma, almacenamiento y firma de recepción | ✅ Listado (con buscador) + registro + detalle con custodia |
| **RF02** Ingreso de resultados | ✅ | ✅ Asignación de análisis + diálogo de ingreso |
| **RF03** Validación de rangos | ✅ | ✅ Aviso en vivo y observaciones obligatorias |
| **RF04** Aprobación por supervisor | ✅ Con reglas de rol, segregación y evaluación única | ✅ Pantalla de Aprobaciones |
| **RF05** Reporte PDF | ✅ OpenPDF, versionado y registro en `reportes` | ✅ Generar, vista previa y guardar |
| **RF06** Autenticación con roles | ✅ Sesión con token, permisos por rol en `SecurityConfig`, usuario tomado de la sesión, registro público solo como Analista | ✅ Token en todas las peticiones, vuelta al login si expira, barra lateral por rol y pantalla de Usuarios |

Además: historial de estados de la muestra (ISO 17025) ✅ · **investigadores, almacenamiento y cadena de custodia firmada (v4)** ✅ · **tabla de rangos normales visible** ✅.

### Próximos pasos

1. Definir el flujo después de un **rechazo** (reingreso o corrección del resultado).
2. Pruebas JUnit de los servicios.
3. Cargar en el catálogo los **parámetros reales de algas** (clorofila-a, salinidad, pH, temperatura, nutrientes) con su rango y **fuente** (tarea de QA/documentación).
4. Instalador (`.exe`/`.jar`), video demo y actualización del informe técnico.

## Estado del proyecto (seguridad)

La seguridad de RF06 está implementada en el paquete `security` del backend:

- `SesionService` — crea, valida, expira y cierra las sesiones (token aleatorio de 256 bits).
- `TokenAuthFilter` — lee `Authorization: Bearer …` en cada petición y registra al usuario con su rol (`ROLE_ANALISTA`, `ROLE_SUPERVISOR`, `ROLE_ADMINISTRADOR`).
- `UsuarioAutenticado` — el usuario de la sesión; los controllers lo reciben con `@AuthenticationPrincipal`.
- `config/SecurityConfig` — qué rutas son públicas y qué rol exige cada acción (tabla en la sección 6).
- `config/DatosInicialesConfig` — crea el admin inicial si la BD no tiene usuarios.

Las reglas de negocio de RF04 (solo Supervisor/Administrador aprueban, nunca su propio resultado, una sola evaluación) se mantienen además en `AprobacionService` como segunda capa de validación.

Pendiente / fuera de alcance del MVP: **HTTPS** (RNF04) — hoy el cliente habla con `http://localhost:8080`; en una instalación real el backend debería exponerse detrás de un certificado TLS.

## 7. (Opcional) Usar la base de datos compartida en la nube (Aiven)

Además de la base local en Docker, el equipo tiene una base MySQL en la nube
(Aiven) para compartir los mismos datos entre todos. El backend puede apuntar
a la base local o a la de la nube **sin tocar código**, usando un perfil de
Spring. El detalle completo está en `README-aiven.md`.

Resumen:

1. Pide a Nicolás **por mensaje privado** los datos de conexión (nunca por el
   grupo ni por git).
2. Crea `lims-backend/src/main/resources/application-aiven.properties` (ya está
   en el `.gitignore`, no se sube):
```properties
   spring.datasource.url=jdbc:mysql://HOST:PORT/lims_db?sslMode=REQUIRED
   spring.datasource.username=USUARIO
   spring.datasource.password=CLAVE
```
3. En IntelliJ, crea una configuración Spring Boot para
   `LimsBackendApplication` con la variable de entorno
   `SPRING_PROFILES_ACTIVE=aiven`.
4. Ejecuta esa configuración. En el log verás
   `The following profiles are active: aiven`.

   Desde PowerShell, sin IntelliJ:
   ```powershell
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=aiven"
   ```
   Sin el perfil (`.\mvnw.cmd spring-boot:run`) el backend usa la BD **local** de Docker.

> - La base de Aiven ya tiene esquema y datos: **no correr** `01-schema.sql`
>   contra ella. Para ponerla al día ejecuten **una vez** `db-migrations/v4-dominio-ambiental.sql`
>   (y, si su esquema es muy antiguo, antes el `ALTER TABLE usuarios ADD COLUMN requiere_reset_password ...`
>   de la sección 2). Como Aiven ya tiene usuarios, el admin inicial no se crea: asignen el rol
>   Administrador a un usuario existente con `UPDATE usuarios SET id_rol = 1 WHERE username = '...';`
> - Después de migrar, desactiven en *Parámetros y rangos* los análisis clínicos de prueba.
> - Solo un backend a la vez (local o Aiven): comparten el puerto 8080.
> - El plan gratuito de Aiven se suspende tras inactividad; la primera
>   conexión puede tardar en "despertar".
