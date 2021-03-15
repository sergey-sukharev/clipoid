package dev.sukharev.clipangel.presentation.fragments.cliplist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dev.sukharev.clipangel.data.local.repository.channel.ChannelRepository
import dev.sukharev.clipangel.data.local.repository.clip.ClipRepository
import dev.sukharev.clipangel.domain.channel.models.Channel
import dev.sukharev.clipangel.domain.clip.Clip
import dev.sukharev.clipangel.domain.models.asSuccess
import dev.sukharev.clipangel.domain.models.isSuccess
import dev.sukharev.clipangel.presentation.models.Category
import dev.sukharev.clipangel.utils.copyInClipboardWithToast
import dev.sukharev.clipangel.utils.toDateFormat1
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.zip

class ClipListViewModel(private val clipRepository: ClipRepository,
                        private val channelRepository: ChannelRepository) : ViewModel() {

    private val _clipItemsLiveData: MutableLiveData<List<ClipItemViewHolder.Model>> = MutableLiveData()
    val clipItemsLiveData: LiveData<List<ClipItemViewHolder.Model>> = _clipItemsLiveData

    private val _detailedClip: MutableLiveData<DetailedClipModel> = MutableLiveData()
    val detailedClip: LiveData<DetailedClipModel> = _detailedClip

    private val _copyClipData: MutableLiveData<String> = MutableLiveData()
    val copyClipData: LiveData<String> = _copyClipData

    private val _onDeleteClip: MutableLiveData<Boolean> = MutableLiveData()
    val onDeleteClip: LiveData<Boolean> = _onDeleteClip

    val categoryTypeLiveData = MutableLiveData<Category>(Category.All())

    private val _errorLiveData = MutableLiveData<Throwable>(null)
    val errorLiveData: LiveData<Throwable> = _errorLiveData

    private val allClips = mutableListOf<Clip>()

    fun loadClips() {
        CoroutineScope(Dispatchers.IO).launch {
            clipRepository.getAllWithSubscription()
                    .catch { e -> _errorLiveData.postValue(e) }
                    .collect { clips ->
                        allClips.clear()
                        allClips.addAll(clips)
                        changeCategoryType(categoryTypeLiveData.value!!)
                    }
        }
    }

    fun getDetailedClipData(clipId: String) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            clipRepository.getAll().zip(channelRepository.getAll()) { clips, channels ->
                clips.find { it.id == clipId }?.let { clip ->
                    val channelName: String = channels.find { clip.channelId == it.id }?.name
                            ?: "<UNKNOWN>"
                    _detailedClip.postValue(DetailedClipModel(clip.id, channelName,
                            clip.createdTime.toDateFormat1(), clip.data, clip.isFavorite))
                }
            }.collect()
        }
    }

    fun changeCategoryType(type: Category) {
        GlobalScope.launch(Dispatchers.Unconfined) {
            val filteredClipList = when (type) {
                is Category.All -> {
                    allClips
                            .sortedByDescending { it.createdTime }
                }
                is Category.Favorite -> {
                    allClips
                            .sortedByDescending { it.createdTime }
                            .filter { it.isFavorite }
                }
                is Category.Private -> {
                    allClips
                            .sortedByDescending { it.createdTime }
                            .filter { it.isProtected }
                }
            }

            _clipItemsLiveData.postValue(filteredClipList.map {
                ClipItemViewHolder.Model(it.id, it.data, it.isFavorite, it.getCreatedTimeWithFormat())
            })

            categoryTypeLiveData.postValue(type)
        }
    }

    private fun filterClipsByCategory(category: Category) {
        when (category) {
            is Category.All -> {
            }
            is Category.Favorite -> {
            }
            is Category.Private -> {
            }
        }
    }

    fun copyClip(clipId: String) = CoroutineScope(Dispatchers.IO).launch {
        clipRepository.getAll()
                .catch { e -> _errorLiveData.postValue(e) }
                .collect {
            it.find { it.id == clipId }?.apply {
                _copyClipData.postValue(data)
            }
        }
    }

    @ExperimentalCoroutinesApi
    fun markAsFavorite(clipId: String) = CoroutineScope(Dispatchers.IO).launch {
        clipRepository.getClipById(clipId)
                .catch { e -> _errorLiveData.postValue(e) }
                .collect {
                    clipRepository.update(it.apply {
                        it.isFavorite = !it.isFavorite
                    }).catch { e -> _errorLiveData.postValue(e) }
                            .collect {
                                getDetailedClipData(it.id)
                            }
                }
    }

    @ExperimentalCoroutinesApi
    fun deleteClip(clipId: String) = CoroutineScope(Dispatchers.IO).launch {
        clipRepository.getClipById(clipId).collect { clip ->
            clipRepository.delete(clip)
                    .catch { e -> _errorLiveData.postValue(e) }
                    .onCompletion { _onDeleteClip.postValue(true) }
                    .collect()
        }
    }

    data class DetailedClipModel(
            val id: String,
            val channelName: String,
            val createDate: String,
            val data: String,
            val isFavorite: Boolean
    )


}