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

**En GitHub Desktop:**

1. **Fetch origin**.
2. **Branch → Update from main**.
3. **Push origin**.

Si queda una carpeta vacía llamada `Evidencia Proyecto` en la raíz del repo, bórrenla desde el Explorador.

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

Los datos de la base local **no se pierden** con el cambio de ruta: viven en un volumen de Docker que depende del nombre de la carpeta `lims-backend`, y ese nombre no cambió.

## 4. Si sale el error `Unknown column ... id_equipo_toma`

No es por el cambio de ruta: significa que su base local no tiene la **migración v6**.

**Primero revisen qué tienen aplicado** (desde la carpeta `lims-backend`):

```powershell
docker exec lims-mysql mysql -u lims_user -plims_pass lims_db -e "SHOW TABLES LIKE 'sitios_muestreo'; SHOW COLUMNS FROM muestras LIKE 'id_equipo_toma';"
```

| Resultado | Qué significa | Qué hacer |
|---|---|---|
| Aparecen los dos | Ya tienen v5 y v6 | Nada |
| Aparece `sitios_muestreo` pero no `id_equipo_toma` | Tienen v5, les falta v6 | Ejecutar solo la v6 |
| No aparece ninguno | Les faltan v5 y v6 | Ejecutar v5 y luego v6 (en ese orden) |

**Ejecutar las migraciones** (cada una **una sola vez**):

```powershell
Get-Content db-migrations\v5-trazabilidad-sitios-ia.sql | docker exec -i lims-mysql mysql -u lims_user -plims_pass lims_db
Get-Content db-migrations\v6-equipos-terreno-calibraciones.sql | docker exec -i lims-mysql mysql -u lims_user -plims_pass lims_db
```

Si habían cargado los datos demo (`supervisor.demo` / `analista.demo`), carguen también:

```powershell
Get-Content db-migrations\datos-demo-v6.sql | docker exec -i lims-mysql mysql -u lims_user -plims_pass lims_db
```

Después reinicien el backend (Ctrl+C y `.\mvnw.cmd spring-boot:run`) y vuelvan a iniciar sesión en el cliente.

- La advertencia `Using a password on the command line interface can be insecure` es normal.
- Si aparece `Duplicate column name` o `already exists`, esa parte ya estaba aplicada: **no repitan el script** y avisen en el grupo.

## 5. Problemas comunes

| Error | Causa | Solución |
|---|---|---|
| `no configuration file provided: not found` | `docker compose` se ejecutó fuera de `lims-backend` | Hacer `cd` a la carpeta `lims-backend` primero |
| `mvnw.cmd no se reconoce...` | PowerShell no ejecuta archivos de la carpeta actual sin `.\` | Usar `.\mvnw.cmd` |
| IntelliJ dice que el proyecto no existe | Está abriendo la ruta vieja | **File → Open** con la ruta nueva |
| Aparece `.idea/` en GitHub Desktop | Configuración local de IntelliJ | No subirla; el `.gitignore` de la raíz ya la ignora |
