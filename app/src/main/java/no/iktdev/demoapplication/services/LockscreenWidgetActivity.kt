package no.iktdev.demoapplication.services

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import no.iktdev.demoapplication.R


// https://stackoverflow.com/questions/48277302/android-o-flag-show-when-locked-is-deprecated
// https://stackoverflow.com/questions/60732308/android-displaying-view-over-the-lockscreen-like-google-maps
// https://stackoverflow.com/questions/24748442/floating-view-on-android-screen

// https://www.simplifiedcoding.net/android-floating-widget-tutorial/

// https://stackoverflow.com/questions/58670437/can-we-create-an-popupwindows-outside-of-the-application-android-studio


class LockscreenWidgetActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_lockscreen)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            window.addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }
    }

    private fun showUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }


}