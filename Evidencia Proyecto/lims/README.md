# LIMS - Sistema de Gestión de Información de Laboratorio

Proyecto Capstone (DuocUC) — Cristian Solís, Claudio Murúa, Nicolás Cárdenas.

Aplicación de escritorio en JavaFX conectada a un backend Spring Boot y una base de datos MySQL, para la gestión de muestras, resultados y reportes de un laboratorio de análisis.

## Estructura del repositorio

```
lims/
├── lims-backend/
│   ├── docker-compose.yml   # Levanta la BD MySQL en un contenedor
│   ├── db-init/             # Script(s) SQL de creación de esquema (solo corre en un volumen de BD nuevo)
│   └── src/...              # API REST (Spring Boot + JPA + MySQL)
└── lims-client/             # Cliente de escritorio (JavaFX)
```

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
- **Ya existe un sistema de usuarios real** (ver sección 6 más abajo). Para crear tu primer usuario de prueba, lo más simple es levantar el backend y el cliente (secciones 4 y 5) y usar la pantalla **"Crear usuario"** del login, o hacer un `POST /api/usuarios`. Como alternativa (por ejemplo, si el backend aún no está corriendo y necesitas un usuario para pruebas vía SQL directo), puedes insertarlo manualmente:

  ```
  docker exec -it lims-mysql mysql -u lims_user -plims_pass lims_db
  ```
  Y dentro del prompt `mysql>` ejecuta:
  ```sql
  INSERT INTO usuarios (id_centro, nombre, apellido, email, username, password_hash, id_rol)
  VALUES (1, 'Tu Nombre', 'Tu Apellido', 'tu_correo@lims.cl', 'tu_usuario', 'pendiente-hasta-login', 3);
  ```
  > ⚠️ Un usuario insertado así por SQL directo **no podrá iniciar sesión** por la pantalla de login, porque `password_hash` no queda como un hash BCrypt válido — solo sirve como llave foránea para pruebas de `muestras.usuario_registro`. Para un usuario que sí pueda loguearse, créalo por `POST /api/usuarios` o por la pantalla "Crear usuario" del cliente.

  Donde:
  - `id_centro = 1` → "Laboratorio Central" (precargado en la tabla `centros`).
  - `id_rol`: `1 = Administrador`, `2 = Supervisor`, `3 = Analista` (precargados en la tabla `roles`).

  Si tienes dudas sobre los valores disponibles, revísalos con:
  ```sql
  SELECT * FROM centros;
  SELECT * FROM roles;
  ```
- **Antes de que Claudio y Nicolás clonen el repo**, confirma que `lims-backend/db-init/01-schema.sql` ya tenga los campos `tipo_muestra`, los 3 valores de `prioridad`, y la columna `requiere_reset_password` de `usuarios` — si ese archivo quedó desactualizado, sus bases de datos se crearán con el esquema viejo.
- **Si tu base de datos ya existía antes de este cambio** (es decir, no la recreaste desde cero), necesitas agregar la columna nueva a mano:
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

Verificación rápida: abre el archivo `.http` de pruebas (o un navegador) en `http://localhost:8080/api/usuarios` — debería responder `200 OK` con un arreglo JSON (vacío o con datos, según lo que tenga la BD).

### Datos de prueba con `test.http`

`lims-backend/test.http` contiene el **flujo completo** numerado (ejecútalo en orden sobre una BD recién creada con `docker-compose down -v` + `up -d`):

| Paso | Qué hace |
|---|---|
| 0a / 0b | Crea un **Supervisor** (`cristian` / `1234`, id 1) y una **Analista** (`ana` / `1234`, id 2) |
| 1 – 3b | Crea una muestra, dos análisis del catálogo y los asigna a la muestra |
| 4 – 4b | La analista ingresa los resultados (la muestra pasa a *En analisis* y luego a *Resultados ingresados*) |
| 6a / 6b / 7b | Pruebas que **deben fallar** (400): analista intentando aprobar, rechazo sin comentario, aprobar dos veces |
| 7 – 8 | El supervisor aprueba ambos resultados (la muestra pasa a *Aprobada*) |
| 9 | Detalle de la muestra con análisis, resultados y **historial de estados** |

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
| Inicio de sesión / Crear usuario / Recuperar contraseña | Al abrir la app | Autenticarse contra el backend (RF06) |
| **Listado de muestras** (mockup 11.2) | Barra lateral → *Muestras* | Tabla con estados en etiquetas de color, **buscador en vivo** por código/cliente/tipo, doble clic para abrir el detalle |
| **Registro de muestra** (mockup 11.3) | *+ Nueva muestra* | Crear una muestra (queda registrada a nombre del usuario conectado) |
| **Detalle de muestra** | Doble clic o *Ver detalle* en el listado | Datos de la muestra; pestaña **Análisis solicitados** (asignar análisis del catálogo e ingresar resultados) y pestaña **Historial de estados** |
| **Ingreso de resultado** (mockup 11.4) | Detalle → seleccionar análisis → *Ingresar resultado* | Diálogo que avisa **en vivo** si el valor está fuera de rango y exige observaciones (RF02/RF03). Acepta coma decimal (`7,2`) |
| **Aprobación de resultados** (mockup 11.5) | Barra lateral → *Aprobaciones* | Tabla de resultados pendientes + panel de revisión con botones **Aprobar** / **Rechazar** (comentario obligatorio al rechazar) (RF04) |

Las opciones *Análisis, Reportes, Usuarios y Configuración* de la barra lateral aparecen deshabilitadas ("próximamente").

### Estructura del cliente (lo más importante)

- `Navigator.java` — toda la navegación. Login, Crear usuario y Recuperar contraseña ocupan la ventana completa; el resto de pantallas se cargan **dentro** del marco (`main-layout.fxml` + `MainLayoutController`).
- `service/ApiClient.java` — único punto de contacto con el backend (HTTP + JSON).
- `model/` — modelos del cliente. Los nuevos son `record` de Java 21 (`MuestraDetalle`, `AnalisisCatalogo`, `ResultadoPendiente`, etc.): se accede con `nombre()` en vez de `getNombre()`.
- `ui/Badges.java` — etiquetas de color para estados (se usan en todas las tablas con `col.setCellFactory(c -> Badges.celda())`).
- `styles.css` — tema completo de la app (barra lateral, tablas, botones, etiquetas). Los estilos del login están al inicio del archivo y el tema de la app al final.

## 6. Módulos y endpoints del backend (API REST)

El backend expone los siguientes módulos, todos bajo `http://localhost:8080`.

### Muestras (RF01)
- `GET  /api/muestras` — lista todas las muestras.
- `GET  /api/muestras/{id}` — **detalle** de una muestra: sus datos, cada análisis solicitado con su resultado y su última aprobación, y el **historial de estados**.
- `POST /api/muestras` — registra una muestra (genera código único automático y deja el primer registro del historial: *Recepción de la muestra*).

### Catálogo de análisis
- `GET   /api/analisis` — lista los análisis del catálogo.
- `GET   /api/analisis/{id}` — obtiene un análisis por id.
- `POST  /api/analisis` — crea un análisis (valida que min <= max).
- `PUT   /api/analisis/{id}` — edita un análisis.
- `PATCH /api/analisis/{id}/estado?activo=false` — activa/desactiva (baja lógica).

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
Recibida ──(1er resultado)──► En analisis ──(todos con resultado)──► Resultados ingresados ──(todos aprobados)──► Aprobada
                                                                                         └──(un rechazo)──────► Rechazada
```

Todo cambio de estado pasa por `EstadoMuestraService`, que actualiza la muestra **y** registra una fila en
`muestra_historial_estado` con el estado anterior, el nuevo, el usuario que lo provocó, la fecha y el motivo.
El historial se consulta en `GET /api/muestras/{id}` y se ve en la pestaña *Historial de estados* del cliente.

> Las muestras creadas antes de este cambio no tienen historial (la tabla ya existía en el esquema, solo que nadie la llenaba).
>
> **Pendiente (decisión del equipo):** qué hacer después de un rechazo. Hoy un rechazo deja la muestra en *Rechazada* de forma definitiva; falta definir el flujo de reingreso/corrección del resultado.

### Usuarios y autenticación (RF06)
- `GET  /api/usuarios` — lista todos los usuarios (nunca expone `password_hash`).
- `POST /api/usuarios` — crea un usuario. Body: `idCentro`, `nombre`, `apellido`, `email`, `username`, `password`, `idRol`. La contraseña se guarda con hash BCrypt.
- `POST /api/usuarios/login` — autentica (`username` + `password`). Devuelve los datos del usuario (sin contraseña) si son correctos, o un 400 con mensaje si no. Actualiza `fecha_ultimo_login`.
- `POST /api/usuarios/solicitar-reset` — flujo de "olvidé mi contraseña": marca al usuario (`username`) con `requiereResetPassword = true`, para que un administrador lo resuelva.
- `GET  /api/usuarios/solicitudes-reset` — lista los usuarios con una solicitud de reseteo pendiente (pensado para una futura pantalla de administración).
- `PUT  /api/usuarios/{id}/resetear-password` — define una nueva contraseña para el usuario y limpia la solicitud pendiente. Body: `nuevaPassword`.

> ⚠️ Mientras no exista autorización por rol en `SecurityConfig` (ver sección "Estado del proyecto (seguridad)"), todos estos endpoints son de acceso libre — cualquiera puede llamarlos sin estar autenticado. El cliente JavaFX ya tiene pantallas de Login/Crear usuario/Recuperar contraseña conectadas a estos endpoints, pero la restricción real de "solo un Administrador puede resetear contraseñas" todavía no está aplicada del lado del servidor.

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
- **`401 Unauthorized` o `403 Forbidden` en los endpoints**: el backend ya trae un `SecurityConfig` temporal que permite todo bajo `/api/**` y `/error`. Si aparece de nuevo, revisar que esa clase esté presente y no haya sido sobrescrita.
- **`404 Not Found` en un endpoint que debería existir (ej. `/api/usuarios`)**: primero confirma con `mvnw clean compile` que el archivo del controlador realmente tiene contenido y compiló (`target\classes\...\NombreController.class` debe existir). Si compiló bien, revisa que no haya **dos procesos Java corriendo a la vez** (`Get-Process java`) — un proceso viejo puede seguir "pegado" al puerto 8080 sirviendo código antiguo mientras el nuevo falla en silencio al no poder tomar el puerto.
- **`java.net.ConnectException` en el cliente JavaFX al crear un usuario, iniciar sesión, etc.**: el backend no está corriendo (o se cayó) en `localhost:8080`. Verifica con `Invoke-RestMethod -Uri "http://localhost:8080/api/usuarios" -Method Get` antes de usar el cliente.
- **Error `Column 'fecha_recepcion' cannot be null` al crear una muestra**: la entidad `Muestra` debe tener el campo `fechaRecepcion` anotado con `@CreationTimestamp` (Hibernate), no depender solo del `DEFAULT CURRENT_TIMESTAMP` de MySQL.
- **`Unknown column 'requiere_reset_password'` (error 500 en casi todos los endpoints)**: tu base local en Docker se creó con un esquema anterior. Recréala con `docker-compose down -v` y `docker-compose up -d` (borra los datos locales) o aplica el `ALTER TABLE` de la sección 2.
- **`Communications link failure` al arrancar el backend / `Connection refused` en el puerto 8080**: el backend no logró conectarse a MySQL y se cerró. Si en el log dice `No active profile set`, está usando la BD **local**: abre Docker Desktop y ejecuta `docker-compose up -d`. Si querías usar Aiven, arranca con el perfil (ver sección 7).
- **Aiven: `nslookup` responde `Non-existent domain`**: el servicio de Aiven está **apagado** (el plan gratuito se apaga por inactividad y su dominio desaparece). Quien administra la cuenta debe entrar a console.aiven.io y presionar *Power on*. Mientras tanto se puede trabajar con la BD local.
- **`unnamed classes are a preview feature` al compilar**: un método quedó pegado **fuera** de la clase (después de la última `}` del archivo). Muévelo dentro de la clase.
- **400 "Solo un Supervisor o Administrador puede aprobar…" o "No puedes evaluar un resultado que tú mismo ingresaste"**: no es un error, son las reglas de RF04. Para probar el flujo completo se necesitan dos usuarios: un **Analista** que ingresa el resultado y un **Supervisor** distinto que lo aprueba (ver `test.http`, pasos 0a y 0b).
- **Errores de compilación por paquete no encontrado**: confirmar que el paquete base sea `com.duoc.lims.limsbackend` (backend) y `com.duoc.lims.limsclient` (cliente) en todas las clases — un error de tipeo aquí genera decenas de errores en cadena.
- **VS Code marca en rojo `getNombre()`, `setApellido()`, etc. como "cannot find symbol" en una entidad (`Usuario`, `Rol`, `Centro`)**: es un falso positivo del Language Server de Java, que no reconoce los getters/setters generados por Lombok (`@Getter`/`@Setter`). No afecta la compilación real con Maven. Se puede limpiar con `Ctrl+Shift+P` → "Java: Clean Java Language Server Workspace", o instalando la extensión "Lombok Annotations Support for VS Code".

## Estado de los requisitos funcionales

| Requisito | Backend | Cliente JavaFX |
|---|---|---|
| **RF01** Registro de muestras | ✅ | ✅ Listado (con buscador) + registro + detalle |
| **RF02** Ingreso de resultados | ✅ | ✅ Asignación de análisis + diálogo de ingreso |
| **RF03** Validación de rangos | ✅ | ✅ Aviso en vivo y observaciones obligatorias |
| **RF04** Aprobación por supervisor | ✅ Con reglas de rol, segregación y evaluación única | ✅ Pantalla de Aprobaciones |
| **RF05** Reporte PDF | ❌ Pendiente | ❌ Pendiente (mockup 11.6) |
| **RF06** Autenticación con roles | ⚠️ Login con BCrypt; faltan restricciones por rol en los endpoints | ⚠️ Barra lateral con usuario y cierre de sesión; falta mostrar opciones según el rol |

Además: historial de estados de la muestra (ISO 17025) ✅.

### Próximos pasos

1. **RF05** — generación del reporte PDF en el backend + vista previa/descarga en el cliente.
2. **RF06** — seguridad real en `SecurityConfig` (exigir sesión y restringir por rol) y barra lateral según el rol del usuario.
3. Definir el flujo después de un **rechazo** (reingreso o corrección del resultado).
4. Quitar el selector de rol de la pantalla pública "Crear usuario" (hoy cualquiera puede registrarse como Administrador).
5. Pruebas JUnit de los servicios, instalador (`.exe`/`.jar`) y actualización del informe técnico.

## Estado del proyecto (seguridad)

`SecurityConfig.java` actualmente permite todas las peticiones (`permitAll()`) de forma temporal — el login y el registro de usuarios ya funcionan y validan credenciales correctamente, pero **ningún endpoint exige estar autenticado todavía**: cualquiera puede llamar a `/api/**` sin iniciar sesión. Esto está pendiente como tarea de Fase 2 del cronograma (exigir sesión real + restringir acciones por rol, ej. que solo un Administrador pueda resetear contraseñas) y **no debe considerarse la configuración final**.

> Las reglas de negocio de RF04 (solo Supervisor/Administrador aprueban, nunca su propio resultado) **sí** se validan ya en `AprobacionService`, usando el `idSupervisor` que envía el cliente. Lo que falta es que el backend obtenga el usuario desde una sesión autenticada en vez de confiar en ese id.

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
>   contra ella. Si el esquema de Aiven es anterior a este cambio, recuerda
>   aplicar ahí también el `ALTER TABLE usuarios ADD COLUMN requiere_reset_password ...`
>   de la sección 2.
> - Solo un backend a la vez (local o Aiven): comparten el puerto 8080.
> - El plan gratuito de Aiven se suspende tras inactividad; la primera
>   conexión puede tardar en "despertar".
