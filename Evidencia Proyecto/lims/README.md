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
- Después del primer arranque, hay que insertar manualmente un usuario de prueba (todavía no existe login real), porque `Muestra` requiere un `usuario_registro` válido por llave foránea. **Las tablas `centros` y `roles` ya vienen precargadas por `01-schema.sql`**, así que solo hace falta el `INSERT` en `usuarios`.

  Primero entra al cliente MySQL dentro del contenedor (no pegues SQL directo en PowerShell, PowerShell no entiende sintaxis SQL):
  ```
  docker exec -it lims-mysql mysql -u lims_user -plims_pass lims_db
  ```
  Y dentro del prompt `mysql>` ejecuta:
  ```sql
  INSERT INTO usuarios (id_centro, nombre, apellido, email, username, password_hash, id_rol)
  VALUES (1, 'Tu Nombre', 'Tu Apellido', 'tu_correo@lims.cl', 'tu_usuario', 'pendiente-hasta-login', 3);
  ```
  Donde:
  - `id_centro = 1` → "Laboratorio Central" (precargado en la tabla `centros`).
  - `id_rol`: `1 = Administrador`, `2 = Supervisor`, `3 = Analista` (precargados en la tabla `roles`).

  Si tienes dudas sobre los valores disponibles, revísalos con:
  ```sql
  SELECT * FROM centros;
  SELECT * FROM roles;
  ```
- **Antes de que Claudio y Nicolás clonen el repo**, confirma que `lims-backend/db-init/01-schema.sql` ya tenga los campos `tipo_muestra` y los 3 valores de `prioridad` que agregamos a mano por `ALTER TABLE` — si ese archivo quedó desactualizado, sus bases de datos se crearán con el esquema viejo.

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

Verificación rápida: abre el archivo `.http` de pruebas (o un navegador) en `http://localhost:8080/api/muestras` — debería responder `200 OK` con un arreglo JSON (vacío o con datos, según lo que tenga la BD).

## 5. Levantar el cliente JavaFX

En otra terminal:

```
cd lims-client
.\mvnw.cmd clean compile
.\mvnw.cmd javafx:run
```

Debería abrirse la ventana "LIMS - Listado de Muestras" mostrando los datos que devuelve el backend, y desde ahí se puede usar "Agregar Muestra" para crear un registro real de extremo a extremo.

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
- **Error `Column 'fecha_recepcion' cannot be null` al crear una muestra**: la entidad `Muestra` debe tener el campo `fechaRecepcion` anotado con `@CreationTimestamp` (Hibernate), no depender solo del `DEFAULT CURRENT_TIMESTAMP` de MySQL.
- **Errores de compilación por paquete no encontrado**: confirmar que el paquete base sea `com.duoc.lims.limsbackend` (backend) y `com.duoc.lims.limsclient` (cliente) en todas las clases — un error de tipeo aquí genera decenas de errores en cadena.

## Estado del proyecto (seguridad)

`SecurityConfig.java` actualmente permite todas las peticiones (`permitAll()`) de forma temporal, sin login ni roles. Esto está pendiente como tarea de Fase 2 del cronograma (autenticación real + roles Analista/Supervisor) y **no debe considerarse la configuración final**.

## 6. (Opcional) Usar la base de datos compartida en la nube (Aiven)

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

> - La base de Aiven ya tiene esquema y datos: **no correr** `01-schema.sql`
>   contra ella.
> - Solo un backend a la vez (local o Aiven): comparten el puerto 8080.
> - El plan gratuito de Aiven se suspende tras inactividad; la primera
>   conexión puede tardar en "despertar".
