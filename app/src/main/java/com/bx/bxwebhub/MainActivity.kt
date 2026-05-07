package com.bx.bxwebhub

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var menuContainer: View
    private lateinit var appListContainer: LinearLayout
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: TextView

    private var scriptToInject: String = ""

    private var defaultUserAgent: String = ""

    // Le faux profil PC Windows
    private val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val PRODUCTION_JSON_URL = "https://raw.githubusercontent.com/syllebra/web-hub-content/refs/heads/main/list.json"

    // --- 1. JSON MIS À JOUR ---
    // Ajout de "initScale": 100 sur le deuxième jeu pour tester
    private val FALLBACK_JSON = """
        [
          { 
            "name": "Jeu Complexe", 
            "description": "Nécessite plusieurs scripts.",
            "startUrl": "https://...", 
            "scripts": [
                "https://serveur.com/librairie-base.js",
                "https://serveur.com/mon-script-principal.js"
            ],
            "initScale": 0
          }
        ]
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        menuContainer = findViewById(R.id.menuContainer)
        appListContainer = findViewById(R.id.appListContainer)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingText = findViewById(R.id.loadingText)

        setupWebView()
        setupBackButton()
        fetchConfigAndInitMenu()
    }

    private fun setupWebView() {
        // On sauvegarde le User-Agent par défaut de la TV ---
        defaultUserAgent = webView.settings.userAgentString

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true

            // Réglages de base pour imiter un affichage bureau
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100
        }

        // 1. Désactive le zoom de la page
        webView.settings.setSupportZoom(false)
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false

// 2. Bloque le grossissement du texte lié aux paramètres d'accessibilité du téléphone
        webView.settings.textZoom = 100

// 3. Force le viewport web à correspondre aux dimensions exactes de la WebView
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true

        webView.isFocusable = true

        webView.isFocusableInTouchMode = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loadingOverlay.visibility = View.VISIBLE
                loadingText.text = "Connexion..."
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingOverlay.visibility = View.GONE
                if (scriptToInject.isNotEmpty()) {
                    view?.evaluateJavascript(scriptToInject, null)
                }
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                url?.let {
                    val fileName = it.substringAfterLast("/")
                    if (fileName.isNotBlank() && fileName.length < 25 && loadingOverlay.visibility == View.VISIBLE) {
                        loadingText.text = fileName
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) loadingOverlay.visibility = View.VISIBLE
            }
        }
    }

    private fun fetchConfigAndInitMenu() {
        CoroutineScope(Dispatchers.IO).launch {
            val jsonString = try {
                URL(PRODUCTION_JSON_URL).readText()
            } catch (e: Exception) {
                FALLBACK_JSON
            }

            withContext(Dispatchers.Main) {
                buildNativeMenu(jsonString)
            }
        }
    }

    private fun createRoundedBackground(bgColor: String, borderColor: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor(Color.parseColor(bgColor))
            setStroke(3, Color.parseColor(borderColor))
        }
    }

    private fun buildNativeMenu(jsonString: String) {
        appListContainer.removeAllViews()

        try {
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val appConfig = jsonArray.getJSONObject(i)
                val appName = appConfig.getString("name")
                val description = appConfig.optString("description", "Appuyez sur Entrée pour jouer")
                val startUrl = appConfig.getString("startUrl")
                val initScale = appConfig.optInt("initScale", 0)
                val forceDesktop = appConfig.optBoolean("forceDesktop", false)

                // 1. Création de la liste de scripts (remplace l'ancien jsScriptUrl)
                val scriptUrls = mutableListOf<String>()

                val scriptsArray = appConfig.optJSONArray("scripts")
                if (scriptsArray != null) {
                    for (j in 0 until scriptsArray.length()) {
                        scriptUrls.add(scriptsArray.getString(j))
                    }
                } else {
                    val singleScript = appConfig.optString("jsScriptUrl", "")
                    if (singleScript.isNotEmpty()) scriptUrls.add(singleScript)
                }

                // 2. Création de l'interface
                val cardLayout = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(40, 48, 40, 48)

                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 16, 0, 16) }

                    isFocusable = true
                    isFocusableInTouchMode = true
                    background = createRoundedBackground("#1e293b", "#334155")

                    setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            v.background = createRoundedBackground("#2563eb", "#60a5fa")
                            v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(200).start()
                        } else {
                            v.background = createRoundedBackground("#1e293b", "#334155")
                            v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(200).start()
                        }
                    }

                    // 3. LA CORRECTION EST ICI : On envoie 'scriptUrls'
                    setOnClickListener {
                        launchApp(startUrl, scriptUrls, initScale, forceDesktop)
                    }
                }

                val titleView = TextView(this@MainActivity).apply {
                    text = appName
                    textSize = 24f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }

                val descView = TextView(this@MainActivity).apply {
                    text = description
                    textSize = 14f
                    setTextColor(Color.parseColor("#94a3b8"))
                    gravity = Gravity.CENTER
                    setPadding(0, 16, 0, 0)
                }

                cardLayout.addView(titleView)
                cardLayout.addView(descView)
                appListContainer.addView(cardLayout)
            }

            if (appListContainer.childCount > 0) {
                appListContainer.getChildAt(0).requestFocus()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // On remplace `jsScriptUrl: String` par `scriptUrls: List<String>`
    private fun launchApp(startUrl: String, scriptUrls: List<String>, initScale: Int, forceDesktop: Boolean) {
        menuContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
        loadingOverlay.visibility = View.VISIBLE
        loadingText.text = "Préparation..."

        webView.setInitialScale(initScale)

        if (forceDesktop) {
            webView.settings.userAgentString = DESKTOP_USER_AGENT
        } else {
            webView.settings.userAgentString = defaultUserAgent
        }

        webView.requestFocus()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // --- NOUVEAU : Fusion des scripts ---
                val combinedScript = java.lang.StringBuilder()

                for (url in scriptUrls) {
                    withContext(Dispatchers.Main) {
                        // Indique discrètement quel script est en cours de téléchargement
                        loadingText.text = "DL: ${url.substringAfterLast("/")}"
                    }

                    val code = URL(url).readText()

                    // On ajoute le code téléchargé à notre gros script final
                    // On place un ";" et un saut de ligne entre chaque script
                    // pour éviter les erreurs de syntaxe si un fichier est mal terminé
                    combinedScript.append(code).append("\n;\n")
                }

                // On enregistre le méga-script dans la variable globale
                scriptToInject = combinedScript.toString()
                // ------------------------------------

                withContext(Dispatchers.Main) {
                    loadingText.text = "Chargement du site..."
                    webView.loadUrl(startUrl)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Si le téléchargement d'un script échoue, on lance quand même la page
                withContext(Dispatchers.Main) { webView.loadUrl(startUrl) }
            }
        }
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.visibility == View.VISIBLE) {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        webView.visibility = View.GONE
                        webView.loadUrl("about:blank")
                        menuContainer.visibility = View.VISIBLE
                        scriptToInject = ""
                        if (appListContainer.childCount > 0) appListContainer.getChildAt(0).requestFocus()
                    }
                } else {
                    finishAndRemoveTask()
                    System.exit(0)
                }
            }
        })
    }
}