package no.iktdev.demoapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.iktdev.demoapplication.databinding.ActivityMainBinding
import no.iktdev.demoapplication.services.LockscreenWidgetActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)


        binding.startLockActivity.setOnClickListener {
            startActivity(Intent(applicationContext, LockscreenWidgetActivity::class.java))
        }


        binding.startScreen.setOnClickListener {
            startActivity(Intent(applicationContext, OverlayActivity::class.java))
            lifecycleScope.launch(Dispatchers.IO) {
                delay(500)
                withContext(Dispatchers.Main) {
                    this@MainActivity.finish()
                }
            }
        }
    }
}