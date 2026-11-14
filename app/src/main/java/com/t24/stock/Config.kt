package com.t24.stock

/**
 * Configuración de la APK.
 *
 * API_URL: pegá acá la URL de la implementación de Apps Script (la que termina en /exec).
 * La muestra T24_API_PRUEBA() en el registro del editor ("urlParaLaApk").
 */
object Config {
    const val API_URL = "https://script.google.com/macros/s/AKfycbwqzbbx6GfSuEy-WbwyNfVb--T3Gnru87hpetct_S53qsCtn7QcMngn4mNoip8e4nS-/exec"

    /** Repositorio de GitHub donde se compila y publica la APK (actualizaciones de la parte Android). */
    const val GITHUB_REPO = "oscaralejandrojuarezleal-beep/stock-t24-apk"

    /** Dirección interna desde donde el WebView carga la app (no es internet). */
    const val URL_INICIO = "https://appassets.androidplatform.net/index.html"

    /** Nombre del archivo de preferencias interno. */
    const val PREFS = "t24_apk"

    /** Nombre con el que la app web ve a Android: window.T24Android */
    const val PUENTE = "T24Android"

    /** Función del servidor que entrega la app completa. */
    const val FN_APP = "T24_apiAppApk"

    /** Función liviana que devuelve la "huella" de la app publicada. */
    const val FN_HUELLA = "T24_apiHuellaApp"

    /** Nombre del archivo APK publicado en GitHub. */
    const val ARCHIVO_APK = "Stock-T24.apk"

    /** Nombre que se muestra al imprimir, notificar y guardar. */
    const val NOMBRE = "Stock T24"

}
