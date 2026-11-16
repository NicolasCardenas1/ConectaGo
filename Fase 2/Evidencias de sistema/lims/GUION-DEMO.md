# Guion de demostración — ConectaGo LIMS (v5)

Demo de 12 minutos pensada para responder, en vivo, cada punto de la retroalimentación de Fase 2.

| Comentario del docente | Dónde se responde en la demo |
|---|---|
| "Aún no se ve claridad con lo que quieren entregar" | Paso 0 (frase de producto) |
| "Qué diferencia hay con un Excel" | Pasos 4, 5 y 6 (y la tabla del README) |
| "No veo trazabilidad de los datos de análisis" | Pasos 4, 5 y 6 |
| "Base de datos de sitios de muestreo (geolocalización)" | Paso 2 |
| "Pueden utilizar datos desplegables" | Paso 3 |
| "Pueden aplicar inteligencia artificial" | Pasos 4 y 7 |
| "Está un poco pobre" | Paso 1 (panel) y la demo completa |
| "No indican el aporte de cada uno" | Cada integrante presenta su parte (tabla al final) |

---

## Antes de la demo (una vez, la noche anterior)

1. Docker Desktop abierto. En `lims-backend`:
   ```powershell
   docker-compose up -d
   Get-Content db-migrations\v5-trazabilidad-sitios-ia.sql | docker exec -i lims-mysql mysql -u lims_user -plims_pass lims_db   # solo si la BD es anterior a v5
   Get-Content db-migrations\datos-demo-v5.sql | docker exec -i lims-mysql mysql -u lims_user -plims_pass lims_db
   ```
   > Lo más limpio es partir de una BD nueva (`docker-compose down -v` + `up -d`) y cargar solo `datos-demo-v5.sql`.
2. (Opcional) Configurar `ANTHROPIC_API_KEY` en la configuración de IntelliJ del backend, para que el borrador de interpretación lo escriba Claude. Sin ella, la demo funciona igual con el borrador por reglas.
3. Levantar backend y cliente. Verificar **internet** (el mapa usa OpenStreetMap).
4. Tener abiertas **dos sesiones**: una como `analista.demo` / `analista123` y otra como `supervisor.demo` / `super123`. Se puede hacer con dos clientes, o cerrando sesión entre pasos.

---

## Paso 0 — Qué es (30 s) · *Product Owner*

> "ConectaGo LIMS es una aplicación de escritorio para el laboratorio de investigación ambiental de Los Lagos que garantiza la **trazabilidad completa de cada muestra y de cada dato**, desde el sitio de muestreo hasta el informe aprobado que recibe la investigadora."

## Paso 1 — Panel de inicio (1 min) · como **supervisor**

- Indicadores: muestras, pendientes, fuera de rango, **valores atípicos**, tiempo promedio hasta la aprobación, **equipos con calibración vencida**.
- **Alertas de calidad**: EQ-005 (balanza) con la calibración vencida y la sonda por vencer.
- **Tendencia**: clorofila-a en Bahía Ilque durante 8 meses, con los límites del rango normal. Cambiar el sitio a *Isla Tenglo* para mostrar la **floración algal de junio** (fuera de rango, documentada).

## Paso 2 — Sitios y mapa (1 min)

- *Sitios y mapa*: los 6 sitios en el mapa, coloreados por estado (naranjo = tiene resultados fuera de rango).
- Clic en un sitio → popup con coordenadas, cantidad de muestras y último muestreo.
- **Clic en el mapa** → se abre el formulario de *nuevo sitio* con las coordenadas ya llenas.

## Paso 3 — Registrar una muestra con datos desplegables (1,5 min) · como **analista**

- *+ Registrar muestra*: investigadora Paula Rivas, tipo *Macroalga*, **sitio ST-001 desde la lista** (y "punto exacto": *estación 3*), condición *Refrigerada 4 °C* y ubicación *Refrigerador 4 °C · Estante 1* **desde listas**, firma de recepción.
- En el detalle: agregar los análisis *Clorofila-a* y *Salinidad*.

## Paso 4 — Ingreso con equipo calibrado y detección de anomalías (2 min) · **analista**

1. *Ingresar resultado* en Salinidad → escribir **27**.
   - "✓ Dentro del rango normal" (20–34 PSU)…
   - …pero **"⚠ Valor atípico: puntaje −8,7"**: en Bahía Ilque la salinidad ronda 31,5. **Esto es lo que un Excel con validación de rango no ve.**
2. Elegir el equipo **EQ-005 (calibración vencida)** → el sistema **no deja usarlo**. Elegir **EQ-003** y guardar.
3. Explicar la IA en una frase: *"compara cada valor con el historial del mismo parámetro en el mismo sitio, usando mediana y MAD, que no se dejan engañar por los propios atípicos; no bloquea, avisa y lo destaca al supervisor."*

## Paso 5 — Rechazo, corrección y versiones (2 min) · **supervisor** y **analista**

1. Supervisor → *Aprobaciones*: el resultado aparece marcado como **Atípico**, con equipo y calibración. Intentar **aprobar sin comentario** → el sistema lo exige. **Rechazar** con el motivo *"Salinidad atípica: repetir con sonda recalibrada"*. La muestra pasa a **Rechazada**.
2. Analista → detalle → *Corregir resultado*: aparece el valor vigente y el motivo del rechazo. Ingresar **31,2** con el motivo *"Relectura tras recalibrar la sonda"* → **versión 2**. La muestra vuelve a revisión.
3. *Ver versiones*: v1 (rechazada, con comentario) y v2 (con motivo). **Ningún valor se borró.**
4. Supervisor aprueba la v2. Analista intenta *Corregir* el resultado aprobado → **bloqueado** ("un dato aprobado no se puede modificar").

> **Mensaje clave contra el Excel:** en una planilla, el 27 se habría sobrescrito por 31,2 sin rastro.

## Paso 6 — Trazabilidad completa (1,5 min)

- Pestaña **Trazabilidad completa** de la muestra: toma en el sitio (con coordenadas) → registro → custodia → análisis → **resultado v1 (atípico, en rojo)** → **rechazo** → **corrección v2 con motivo** → aprobación → estados.
- *"Quién hizo qué, cuándo, con qué equipo y por qué"*: es la exigencia central de ISO/IEC 17025.

## Paso 7 — Interpretación con IA e informe (1,5 min) · **supervisor**

- *Interpretación (IA)* → *Generar borrador con IA*: Claude (o las reglas, si no hay API key) redacta una interpretación a partir de los resultados, rangos y atípicos. **El supervisor la revisa y la edita**; el sistema registra que la validó él.
- *Generar informe PDF*: sitio con coordenadas, **equipo y calibración** por resultado, versión `(v2)`, **historial de correcciones**, **interpretación** con la nota "generado con IA, revisado y validado por…", cadena de custodia.

## Paso 8 — Cierre (30 s)

> "Lo que entregamos no es un formulario que guarda filas: es un sistema que **no deja perder la historia de un dato**, que **avisa cuando algo es raro** y que **no deja usar un equipo descalibrado**. Eso es lo que hoy no hace el papel ni un Excel."

---

## Quién presenta qué (propuesta según el aporte de cada uno)

| Integrante | Pasos | Por qué |
|---|---|---|
| Claudio Murúa (PO) | 0, 1, 4, 6, 7, 8 | Visión del producto, usuarios/roles (RF06) y la v5 (trazabilidad, anomalías, IA, panel) |
| Cristian Solís | 2, 3 | Muestras (RF01), registro, dominio ambiental y cadena de custodia (v4) |
| Nicolás Cárdenas (SM) | 5 | Resultados, rangos y aprobaciones (RF02-RF04), que son la base del flujo de rechazo y corrección |

## Preguntas probables

- **¿Por qué no Excel?** → Tabla del README: permisos por rol, versiones con motivo, equipo calibrado, validación y atípicos automáticos, multiusuario, informe trazable.
- **¿Qué IA usan exactamente?** → Dos cosas distintas. 1) **Detección de anomalías**: estadística robusta (puntaje z modificado con mediana y MAD), explicable y sin cajas negras. 2) **IA generativa** (Claude, de Anthropic) solo para **redactar un borrador**; nunca decide ni aprueba: un supervisor revisa y firma.
- **¿Y si la IA se equivoca o no hay internet?** → El borrador siempre lo revisa un humano. Sin API key o sin conexión, se genera un borrador por reglas y el resto del sistema funciona igual.
- **¿Cumple ISO 17025?** → Está *diseñado según* sus requisitos de trazabilidad (registros inalterables, segregación de funciones, equipos calibrados, justificación de cambios). La acreditación formal es del laboratorio, no del software.
- **¿Los rangos son reales?** → Los de la demo son referenciales; el siguiente paso es validarlos con la investigadora y registrar la fuente en el catálogo.
