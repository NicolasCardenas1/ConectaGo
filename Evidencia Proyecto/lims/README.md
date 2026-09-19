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

- **JDK** — revisa la propiedad `<java.version>` (o `<maven.compiler.release>`) en el `pom.xml` de `lims-backend` y `lims-client` para confirmar la versión exacta e instalar esa misma.
- **Docker Desktop** (para levantar MySQL en un contenedor).
- **Git**.
- **IntelliJ IDEA** (Community o Ultimate) — recomendado, es lo que usa el equipo.
- No es necesario instalar Maven aparte: ambos proyectos incluyen Maven Wrapper (`mvnw` / `mvnw.cmd`).

## 1. Clonar el repositorio

```
git clone <URL-del-repositorio>
cd lims
```

> ⚠️ Cristian: pega aquí la URL real del repo de GitHub del equipo antes de compartir este archivo.

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

Para levantarla, cada integrante solo necesita:

```
cd lims-backend
docker-compose up -d
```

Esto crea el contenedor `lims-mysql`, expuesto en el puerto **3307** del host (para no chocar con un MySQL local que ya tengas en el 3306 por defecto), con usuario `lims_user` / contraseña `lims_pass` sobre el esquema `lims_db`.

> ⚠️ La contraseña de root (`rootpassword`) queda en texto plano en este archivo versionado en git. Es aceptable para un entorno de desarrollo local del equipo, pero no la reutilicen en nada expuesto a internet.

Notas importantes para tus compañeros:
- Los scripts dentro de `lims-backend/db-init/` (como `01-schema.sql`) **solo se ejecutan la primera vez** que se crea el volumen `lims-data`. Si alguien ya había levantado el contenedor antes de un cambio de esquema, hay que recrearlo desde cero:
  ```
  docker-compose down -v
  docker-compose up -d
  ```
  (`-v` borra también el volumen `lims-data`, no solo el contenedor).
- Después del primer arranque, hay que insertar manualmente un usuario de prueba (todavía no existe login real), porque `Muestra` requiere un `usuario_registro` válido por llave foránea:
  ```sql
  INSERT INTO usuarios (id_usuario, nombre, apellido, username, password_hash)
  VALUES (1, 'Tu Nombre', 'Tu Apellido', 'tu_usuario', 'pendiente-hasta-login');
  ```
  > ⚠️ Confirma los nombres de columna reales de tu tabla `usuarios` (puede que falten o sobren columnas respecto a este ejemplo) y ajusta el INSERT.
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

Debería abrirse la ventana "LIMS - Listado de Muestras" mostrando los datos que devuelve el backend.

## Problemas comunes

- **`ClassNotFoundException` al hacer `javafx:run`**: revisar que en `lims-client/pom.xml`, dentro del plugin `javafx-maven-plugin`, el `<mainClass>` esté como clase simple (`com.duoc.lims.limsclient.HelloApplication`), **sin** prefijo de módulo (`module/Class`). El proyecto no usa `module-info.java`.
- **`401 Unauthorized` o `403 Forbidden` en los endpoints**: el backend ya trae un `SecurityConfig` temporal que permite todo bajo `/api/**` y `/error`. Si aparece de nuevo, revisar que esa clase esté presente y no haya sido sobrescrita.
- **Error `Column 'fecha_recepcion' cannot be null` al crear una muestra**: la entidad `Muestra` debe tener el campo `fechaRecepcion` anotado con `@CreationTimestamp` (Hibernate), no depender solo del `DEFAULT CURRENT_TIMESTAMP` de MySQL.
- **Errores de compilación por paquete no encontrado**: confirmar que el paquete base sea `com.duoc.lims.limsbackend` (backend) y `com.duoc.lims.limsclient` (cliente) en todas las clases — un error de tipeo aquí genera decenas de errores en cadena.

## Estado del proyecto (seguridad)

`SecurityConfig.java` actualmente permite todas las peticiones (`permitAll()`) de forma temporal, sin login ni roles. Esto está pendiente como tarea de Fase 2 del cronograma (autenticación real + roles Analista/Supervisor) y **no debe considerarse la configuración final**.
