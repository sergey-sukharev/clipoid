package dev.sukharev.clipangel.presentation.viewmodels.channellist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dev.sukharev.clipangel.presentation.fragments.cliplist.ClipListViewModel

class MainViewModel: ViewModel() {

    private val _openProtectedClipConfirmation = MutableLiveData<String>()
    val openProtectedClipConfirmation: LiveData<String> = _openProtectedClipConfirmation

    val permitAccessForClip = MutableLiveData<String?>(null)

    fun openBiometryDialogForClip(clipId: String) {
        _openProtectedClipConfirmation.value = clipId
    }

    val forceDetail = MutableLiveData<String>(null)

    fun permitAccessForProtectedClip() {
        permitAccessForClip.value = _openProtectedClipConfirmation.value
    }

}