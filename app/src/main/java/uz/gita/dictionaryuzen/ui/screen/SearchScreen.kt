package uz.gita.dictionaryuzen.ui.screen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import uz.gita.dictionaryuzen.R
import uz.gita.dictionaryuzen.data.model.common.WordDataWithCategory
import uz.gita.dictionaryuzen.databinding.ScreenFavoriteBinding
import uz.gita.dictionaryuzen.presenter.SearchViewModel
import uz.gita.dictionaryuzen.presenter.impl.SearchViewModelImpl
import uz.gita.dictionaryuzen.ui.adapter.WordAdapter
import uz.gita.dictionaryuzen.ui.dialog.TranslationBottomSheetDialog
import java.util.*


@AndroidEntryPoint
class SearchScreen : Fragment(R.layout.screen_search) {
    private val binding by viewBinding(ScreenFavoriteBinding::bind)
    private val viewModel: SearchViewModel by viewModels<SearchViewModelImpl>()
    private val adapter by lazy { WordAdapter() }

    private var tts: TextToSpeech? = null
    private var text: String? = null

    override fun onStart() {
        super.onStart()
        viewModel.onCopyWordLiveData.observe(this, onCopyWordObserver)
        viewModel.onShareWordLiveData.observe(this, onShareWordObserver)
        viewModel.outputVoiceWordLiveData.observe(this, outputVoiceWordObserver)
        viewModel.openDialogLiveData.observe(this, openDialogObserver)
        viewModel.setSpeechTextSearchViewLiveData.observe(this, setSpeechTextSearchViewObserver)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.joinWordLiveData.observe(viewLifecycleOwner, joinFavoriteWordObserver)
        adapter.setOnClickFavoriteListener {
            viewModel.updateWord(it.isFav, it.id)
        }
        adapter.setOnClickWordListener {
            viewModel.getTranslationWords(it)
        }
        binding.rvContainerChapterWithLessons.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.rvContainerChapterWithLessons.adapter = adapter
        val fastScroller: FastScroller = createFastScroller(binding.rvContainerChapterWithLessons)
        binding.rvContainerChapterWithLessons.setOnApplyWindowInsetsListener(
            ScrollingViewOnApplyWindowInsetsListener(binding.rvContainerChapterWithLessons, fastScroller)
        )
        viewModel.joinWords()


        binding.appbar.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText == null || newText == "") {
                    viewModel.joinWords()
                } else {
                    viewModel.search(newText.trim())
                }
                return true
            }
        })
        binding.appbar.imgBtnMicrophone.setOnClickListener {
            viewModel.onSpeech()
        }
    }

    private val joinFavoriteWordObserver = Observer<Cursor> {
        adapter.submitCursor(cursor = it, binding.appbar.searchView.query.toString())
    }
    private val onCopyWordObserver = Observer<String> {
        val clipboard: ClipboardManager? =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = ClipData.newPlainText("Word-So'z ", it)
        clip?.let {
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "copy", Toast.LENGTH_SHORT).show()
        }
    }

    private val onShareWordObserver = Observer<String> {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "type/plain"
        val body = "Word-So'z"
        val sub = it
        intent.putExtra(Intent.EXTRA_TEXT, body)
        intent.putExtra(Intent.EXTRA_TEXT, sub)
        startActivity(Intent.createChooser(intent, ""))
    }

    private val outputVoiceWordObserver = Observer<String> { word ->
        tts = TextToSpeech(requireContext(), TextToSpeech.OnInitListener {
            if (it == TextToSpeech.SUCCESS) {
                tts?.let {
                    it.language = Locale.US
                    it.setSpeechRate(1.0f)
                    it.speak(word, TextToSpeech.QUEUE_ADD, null)
                }
            }
        })
    }
    private val openDialogObserver = Observer<List<WordDataWithCategory>> {
        val dialog = TranslationBottomSheetDialog(requireContext())
        dialog.setWord(it)
        dialog.setOnClickCopyListener {
            viewModel.onCopy(it)
        }
        dialog.setOnClickFavoriteListener {
            viewModel.updateWord(it.isFav, it.id)
        }
        dialog.setOnClickShareListener {
            viewModel.onShare(it)
        }
        dialog.setOnClickVoiceListener {
            viewModel.onOutputVoice(it)
        }
    }
    private val setSpeechTextSearchViewObserver = Observer<Unit> {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Toast.makeText(requireContext(), "isRecog", Toast.LENGTH_SHORT).show()
        } else {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say / Gapiring")
            startActivityForResult(i, 102)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 102 && resultCode == Activity.RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            text = result?.get(0).toString()
            text?.let {
                viewModel.search(it)
                binding.appbar.searchView.setQuery(text, true)
            }
        }
    }
    private fun createFastScroller(recyclerView: RecyclerView): FastScroller {
        return FastScrollerBuilder(recyclerView).useMd2Style().build()
    }
}