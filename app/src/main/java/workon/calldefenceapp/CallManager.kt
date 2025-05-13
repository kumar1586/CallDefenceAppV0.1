package workon.calldefenceapp

import android.telecom.Call
import android.telecom.VideoProfile

object CallManager {
    private var currentCall: Call? = null
    
    fun setCurrentCall(call: Call?) {
        currentCall = call
    }

    fun acceptCall() {
        currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun rejectCall() {
        currentCall?.reject(false, null)
    }

    fun endCall() {
        currentCall?.disconnect()
    }
} 