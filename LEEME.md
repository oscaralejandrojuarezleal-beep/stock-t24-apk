# APK Stock T24

App Android (Kotlin) que muestra **la misma app de Apps Script de Stock**, guardada en el celular o tablet.
Sin Firebase, 100 % gratis.

## Cómo funciona

- **Una sola base de código:** la APK baja la app completa desde el script (`T24_apiAppApk`, que usa
  el mismo `doGet` de la web). Lo que cambiás en Apps Script le llega a la web y a la APK.
- **Rápida y estable:** la app queda guardada en el equipo y abre al instante. Bootstrap, íconos y jsPDF
  también quedan guardados (abre completa aunque no haya internet).
- **Sincronización:** es la misma de la web (revisiones + sólo cambios, líder entre pestañas, modo reposo).
  Cada llamada va al mismo `/exec` por `doPost` (`api-rest-apk-servidor`).
- **Actualización automática:** al hacer **Implementar → Nueva versión** en Apps Script, la "huella" de la app
  cambia sola y en la APK aparece "Hay una versión nueva → Actualizar". No hay números que tocar.

## Dos tipos de actualización

1. **Pantallas, estilos y lógica (Apps Script):** sólo Implementar → Nueva versión. Las APK lo detectan
   (al abrir y cada 10 minutos) y ofrecen actualizar; si tocan "Después", no insiste hasta el día siguiente.
2. **Parte Android (este repositorio):** cada cambio en `main` lo compila GitHub (pestaña **Actions**) y lo
   publica en **Releases** como `Stock-T24.apk`. Las apps instaladas ofrecen "Instalar".

## Archivos principales (app/src/main/java/com/t24/stock)

| Archivo | Para qué |
|---|---|
| `Config.kt` | URL /exec del script y nombre del repositorio |
| `MainActivity.kt` | La pantalla: WebView, botón Atrás, links externos, subir archivos |
| `PaginasLocales.kt` | Sirve la app guardada en el equipo |
| `CacheExterna.kt` | Guarda Bootstrap, íconos, jsPDF y fuentes para usar sin internet |
| `Actualizador.kt` | Descarga la versión nueva de la app (Apps Script) |
| `ActualizadorApk.kt` | Ofrece instalar la APK nueva publicada en GitHub |
| `PuenteJs.kt` | Funciones de Android que usa la app (`window.T24Android`) |
| `Compartir.kt` | Compartir y guardar archivos (Excel, PDF, imágenes) |

## Firma (muy importante)

La APK se firma con una clave fija (`stock-t24.jks`, alias `t24`). Si se pierde, las apps instaladas no
aceptan actualizaciones. La clave **no está en este repositorio**: está en los secretos de GitHub
(`T24_KEYSTORE_B64`, `T24_KEYSTORE_PASSWORD`) y en el archivo de RESPALDO de la PC y de Drive.

## Costo

Cero. Repositorio público + GitHub Actions + Releases son gratis para repositorios públicos.
