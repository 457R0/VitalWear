package com.github.cfogrady.vitalwear.transfer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class CharacterImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(
            this,
            "Character transfer is disabled in companion. Use watch Transfer instead.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }
}

