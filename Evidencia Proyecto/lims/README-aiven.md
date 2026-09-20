# Conexión a la base de datos compartida (Aiven)

Este proyecto puede correr contra dos bases de datos:

- **Local**: tu MySQL en Docker (la de siempre, para pruebas individuales).
- **Aiven**: una base MySQL en la nube, compartida por el equipo. Aquí todos
  vemos las mismas muestras.

La base de Aiven **ya está creada y con datos** (esquema completo + roles,
centro y muestras de ejemplo). No hay que cargar ningún script: solo apuntar
tu backend a ella.

## Requisitos previos

- Tener el repo **actualizado desde `main`** (así ya traes el `.gitignore`
  que protege las credenciales).
- Tener funcionando lo de siempre: JDK 21, IntelliJ y el backend abierto
  como proyecto Maven.

## Paso 1 — Pedir las credenciales

Pídele a Nicolás por **mensaje privado** los datos de conexión de Aiven:
Host, Port, User, Password. (Nunca por el grupo ni subidos a GitHub.)

## Paso 2 — Crear el archivo de conexión

En `lims-backend/src/main/resources/`, crea un archivo llamado:

    application-aiven.properties

Con este contenido (reemplaza los marcadores por los valores que te pasó
Nicolás):

    spring.datasource.url=jdbc:mysql://HOST:PORT/lims_db?sslMode=REQUIRED
    spring.datasource.username=USUARIO
    spring.datasource.password=CLAVE

IMPORTANTE: este archivo NO se sube a git (ya está en el `.gitignore`).
Verifícalo: en el panel de IntelliJ su nombre debe verse en color
ámbar/café (ignorado). Si aparece verde o te pide agregarlo a git, avisa
antes de continuar.

## Paso 3 — Crear la configuración de arranque en IntelliJ

1. Arriba, junto al botón Run, abre el desplegable → **Edit Configurations…**
2. Botón **+** → **Spring Boot**.
3. Completa:
   - **Name:** LimsBackend (Aiven)
   - **Main class:** com.duoc.lims.limsbackend.LimsBackendApplication
   - **Environment variables:** SPRING_PROFILES_ACTIVE=aiven
     (si no ves ese campo: "Modify options" → marca "Environment variables")
4. **Apply** y **OK**.

(Recomendado) Repite para crear una config **LimsBackend (Local)** igual
pero SIN la variable de entorno. Así tienes ambos botones y cambias de base
con un clic.

## Paso 4 — Ejecutar

1. En el desplegable de arriba, elige **LimsBackend (Aiven)** y dale Run.
2. En el log deberías ver:
   - `The following profiles are active: aiven`
   - `Started LimsBackendApplication`
3. Levanta el cliente ejecutando la clase **Launcher**.
4. En el listado de muestras deberían aparecer las muestras compartidas.

## Cómo volver a la base local

No hay que deshacer nada: solo cambias de configuración.

1. Detén el backend de Aiven si está corriendo (cuadrado rojo ⏹), para no
   chocar en el puerto 8080.
2. En el desplegable de arriba, elige **LimsBackend (Local)** (o "Current
   File" sobre `LimsBackendApplication` si no creaste la config Local).
3. Run.

Cómo saber en cuál estás: mira el log de arranque.
- Si aparece `The following profiles are active: aiven` → estás en la NUBE.
- Si NO aparece esa línea → estás en LOCAL.

(Para la base local necesitas Docker corriendo y el contenedor `lims-mysql`
activo, igual que siempre.)

## Cómo consultar la base de Aiven por consola

Útil para revisar datos sin abrir el cliente. Reutiliza el cliente MySQL del
contenedor local (no instala nada); Docker debe estar corriendo.

Conectar (reemplaza HOST, PORT, USUARIO, CLAVE por los datos privados):

    docker exec -it lims-mysql mysql -h HOST -P PORT -u USUARIO -pCLAVE --ssl-mode=REQUIRED lims_db

(La clave va pegada al `-p`, sin espacio.)

Ya dentro del prompt `mysql>`, algunas consultas útiles:

    SHOW TABLES;
    SELECT id_muestra, codigo_unico, tipo_muestra, estado FROM muestras;
    SELECT id_usuario, nombre, id_rol FROM usuarios;

Salir:

    exit

## Notas y problemas comunes

- **Solo un backend a la vez.** Si tienes el local corriendo y arrancas el
  de Aiven (o viceversa), chocan en el puerto 8080. Detén uno antes de
  correr el otro.
- **Primera conexión lenta / falla:** el plan gratuito de Aiven se suspende
  tras un rato de inactividad. Si no conecta al primer intento, espera unos
  segundos a que "despierte" y reintenta.
- **Access denied:** revisa que la clave en `application-aiven.properties`
  esté bien copiada.
- La base de Aiven ya tiene esquema y datos: **no corras** el
  `01-schema.sql` contra ella ni la reinicialices.