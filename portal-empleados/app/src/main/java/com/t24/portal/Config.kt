package com.t24.portal

/** Configuración de la APK del Portal de Empleados T24. */
object Config {
    /** URL de la implementación de Apps Script del Portal (la que termina en /exec). */
    const val URL_PORTAL = "https://script.google.com/macros/s/AKfycbwnkiG0jiSNtf0wFopT0LEdfR4ZfaKJZZHomVSiTz-m3bxtRzk2o9z7qI9V-Jz2--Ha/exec"

    /** Repositorio donde GitHub compila y publica esta APK. */
    const val GITHUB_REPO = "oscaralejandrojuarezleal-beep/stock-t24-apk"

    /** Etiqueta fija de la publicación del Portal (no se mezcla con la de Stock). */
    const val TAG = "portal"

    /** Archivo publicado. */
    const val ARCHIVO_APK = "Portal-T24.apk"

    const val PREFS = "portal_t24"
}
