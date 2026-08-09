package io.github.mgdx.rouelibre.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.databinding.ActivityMainBinding
import io.github.mgdx.rouelibre.ui.map.MapFragment

/**
 * L'unique activité de l'application (SPEC §3).
 *
 * Elle ne fait qu'héberger les fragments. Toute la logique vit dans ceux-ci et
 * dans les modèles de vue.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Le contenu passe sous les barres système, que le thème colore comme
        // le fond : l'écran se lit d'un seul tenant.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sur recréation — rotation, changement de thème — le fragment est
        // restauré par le système ; le replacer effacerait son état.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, MapFragment())
                .commit()
        }
    }
}
