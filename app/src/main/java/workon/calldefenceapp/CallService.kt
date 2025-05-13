package workon.calldefenceapp

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile

class CallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.setCurrentCall(call)
        
        // Show incoming call screen
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("incoming_call", true)
            putExtra("phone_number", call.details.handle?.schemeSpecificPart ?: "Unknown")
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.setCurrentCall(null)
    }
} 