# APK Portal de Empleados T24

App Android que abre la web de Apps Script **T24 · Gestión de Personal** (misma app que la web).
Lo que se cambia en Apps Script llega solo, sin reinstalar.

- URL del portal: `app/src/main/java/com/t24/portal/Config.kt` (`URL_PORTAL`).
- Cada cambio en esta carpeta lo compila GitHub (Actions → "Compilar APK Portal") y lo publica en
  Releases con la etiqueta fija `portal`: https://github.com/oscaralejandrojuarezleal-beep/stock-t24-apk/releases/tag/portal
- Descarga directa: https://github.com/oscaralejandrojuarezleal-beep/stock-t24-apk/releases/download/portal/Portal-T24.apk
- Se firma con la misma clave que Stock. Identidad Android: `ar.t24.portal` (no cambiarla).
