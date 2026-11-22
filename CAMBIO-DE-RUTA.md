# Cambio de ruta del sistema: qué tiene que hacer cada uno

Para cumplir la estructura de evidencias que pide la asignatura, el sistema se movió:

| Antes | Ahora |
|---|---|
| `Evidencia Proyecto/lims` | `Fase 2/Evidencias Proyecto/Evidencias de sistema/lims` |

El código **no cambió**: solo cambió de carpeta. Todas las rutas internas (Maven, Docker, scripts SQL) son relativas, así que siguen funcionando. Los links de los README ya están corregidos.

---

## 1. Antes de que se integre el Pull Request

- [ ] Suban a `main` todo lo que tengan pendiente en su rama (commit + push + PR).
- [ ] Avisen en el grupo cuando estén listos. Mientras tanto, **no creen archivos nuevos** dentro de `Evidencia Proyecto/`: Git no los movería.

## 2. Después de que se integre

**Antes de actualizar, respalden lo que Git no guarda.** Copien a una carpeta **fuera del repo** (por ejemplo `Documentos\ConectaGo-respaldos`):

- `Evidencia Proyecto\lims\lims-backend\src\main\resources\application-aiven.properties` (credenciales de Aiven)
- `Evidencia Proyecto\lims\lims-backend\reportes\` (informes PDF que emitieron)
- `Evidencia Proyecto\lims\lims-backend\.idea\` (configuraciones de ejecución de IntelliJ)

Y bajen el contenedor **sin** `-v` (así no se borra la base), desde la carpeta vieja:

```powershell
cd "C:\ConectaGo\Evidencia Proyecto\lims\lims-backend"
docker compose down
```

**En GitHub Desktop:**

1. **Fetch origin**.
2. **Branch → Update from main**.
3. **Push origin**.

La carpeta `Evidencia Proyecto` **no queda vacía** si tenían esos archivos: quedan ahí, fuera de Git. Muévanlos a la misma ruta dentro de `Fase 2\Evidencias Proyecto\Evidencias de sistema\lims\lims-backend\` y recién entonces borren `Evidencia Proyecto`.

> ⚠️ **Nunca suban `application-aiven.properties`.** Si GitHub Desktop lo muestra como cambio, desmárquenlo: tiene la clave de la base compartida.

**En IntelliJ:**

1. Cierren el proyecto que tenían abierto (la ruta vieja ya no existe).
2. **File → Open** y elijan la ruta nueva:
   `...\ConectaGo\Fase 2\Evidencias Proyecto\Evidencias de sistema\lims`
3. Esperen a que termine de indexar y vuelvan a ejecutar como siempre.

## 3. Levantar el sistema (PowerShell)

Con **Docker Desktop abierto**:

```powershell
# Ventana 1: base de datos y backend
cd "C:\ConectaGo\Fase 2\Evidencias Proyecto\Evidencias de sistema\lims\lims-backend"
docker compose up -d
.\mvnw.cmd spring-boot:run      # esperar "Started LimsBackendApplication"
```

```powershell
# Ventana 2: cliente
cd "C:\ConectaGo\Fase 2\Evidencias Proyecto\Evidencias de sistema\lims\lims-client"
.\mvnw.cmd javafx:run
```

> Cambien `C:\ConectaGo` por la carpeta donde cada uno tiene clonado el repo.
> En PowerShell el `.\` antes de `mvnw.cmd` es obligatorio.

Los datos de la base local **no se pierden** con el cambio de ruta: viven en un volumen de Docker que depende del nombre de la carpeta `lims-backend`, y ese nombre no cambió. **No usen `docker compose down -v`**: la `-v` borra ese volumen con todos los datos.

Si emitieron informes PDF antes del cambio, la base guarda su ruta completa antigua y la descarga falla. Con el backend detenido, corríjanla (cambien `C:\\ConectaGo` por su carpeta, con las barras dobles):

```powershell
docker exec lims-mysql mysql -ulims_user -plims_pass lims_db -e "UPDATE reportes SET ruta_archivo = REPLACE(ruta_archivo, 'C:\\ConectaGo\\Evidencia Proyecto\\lims', 'C:\\ConectaGo\\Fase 2\\Evidencias Proyecto\\Evidencias de sistema\\lims');"
```

## 4. Si sale el error `Unknown column ... id_equipo_toma`

No es por el cambio de ruta: significa que su base local no tiene la **migración v6**.

**Primero respalden la base** (desde la carpeta `lims-backend`; guarden el archivo fuera del repo):

```powershell
docker exec lims-mysql sh -c "mysqldump --single-transaction --no-tablespaces --default-character-set=utf8mb4 -ulims_user -plims_pass lims_db > /tmp/respaldo.sql"
docker cp lims-mysql:/tmp/respaldo.sql "$HOME\Documents\lims_db-respaldo.sql"
```

**Después revisen qué tienen aplicado:**

```powershell
docker exec lims-mysql mysql -u lims_user -plims_pass lims_db -e "SHOW TABLES LIKE 'investigadores'; SHOW TABLES LIKE 'sitios_muestreo'; SHOW COLUMNS FROM muestras LIKE 'id_equipo_toma';"
```

| Resultado | Qué significa | Qué hacer |
|---|---|---|
| Aparecen los tres | Ya tienen v4, v5 y v6 | Nada |
| Aparecen `investigadores` y `sitios_muestreo`, pero no `id_equipo_toma` | Les falta v6 | Ejecutar solo la v6 |
| Aparece solo `investigadores` | Les faltan v5 y v6 | Ejecutar v5 y luego v6 |
| No aparece `investigadores` | Base anterior a v4 | Ejecutar v4, v5 y v6, en ese orden |

**Ejecutar las migraciones** (cada una **una sola vez**, en orden; para la v4 cambien el nombre por `v4-dominio-ambiental.sql`):

> ⚠️ Usen **exactamente** estos comandos. El antiguo `Get-Content archivo.sql | docker exec -i ...` cambia las tildes por `?` en PowerShell y el daño **no se puede revertir** (la custodia deja de abrir con *"Tipo de evento de custodia no válido"*).

```powershell
docker cp db-migrations\v5-trazabilidad-sitios-ia.sql lims-mysql:/tmp/m.sql
docker exec lims-mysql sh -c "mysql --default-character-set=utf8mb4 -ulims_user -plims_pass lims_db < /tmp/m.sql"
docker cp db-migrations\v6-equipos-terreno-calibraciones.sql lims-mysql:/tmp/m.sql
docker exec lims-mysql sh -c "mysql --default-character-set=utf8mb4 -ulims_user -plims_pass lims_db < /tmp/m.sql"
```

Si habían cargado los datos demo (`supervisor.demo` / `analista.demo`), carguen también:

```powershell
docker cp db-migrations\datos-demo-v6.sql lims-mysql:/tmp/m.sql
docker exec lims-mysql sh -c "mysql --default-character-set=utf8mb4 -ulims_user -plims_pass lims_db < /tmp/m.sql"
```

Después reinicien el backend (Ctrl+C y `.\mvnw.cmd spring-boot:run`) y vuelvan a iniciar sesión en el cliente.

- La advertencia `Using a password on the command line interface can be insecure` es normal.
- Si aparece `Duplicate column name`, `Duplicate check constraint name` o `already exists`, esa parte ya estaba aplicada: **no repitan el script** y avisen en el grupo.

## 5. Problemas comunes

| Error | Causa | Solución |
|---|---|---|
| `no configuration file provided: not found` | `docker compose` se ejecutó fuera de `lims-backend` | Hacer `cd` a la carpeta `lims-backend` primero |
| `mvnw.cmd no se reconoce...` | PowerShell no ejecuta archivos de la carpeta actual sin `.\` | Usar `.\mvnw.cmd` |
| IntelliJ dice que el proyecto no existe | Está abriendo la ruta vieja | **File → Open** con la ruta nueva |
| Aparece `.idea/` en GitHub Desktop | Configuración local de IntelliJ | No subirla; el `.gitignore` de la raíz ya la ignora |
| Tildes como `?` (`Recepci??n`) o *"Tipo de evento de custodia no válido"* | Se cargó un script con `Get-Content \| docker exec -i` | Restaurar el respaldo o recrear la base y volver a cargar con `docker cp` |
| Descargar un informe PDF da error | La base guarda la ruta vieja del PDF | Ver el `UPDATE reportes` de la sección 3 |
