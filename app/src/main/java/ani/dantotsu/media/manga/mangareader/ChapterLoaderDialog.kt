package ani.dantotsu.media.manga.mangareader

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.R
import ani.dantotsu.currActivity
import ani.dantotsu.databinding.BottomSheetSelectorBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.media.MediaDetailsViewModel
import ani.dantotsu.media.MediaSingleton
import ani.dantotsu.media.manga.MangaChapter
import ani.dantotsu.others.getSerialized
import ani.dantotsu.tryWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Serializable

class ChapterLoaderDialog : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSelectorBinding? = null
    private val binding get() = _binding!!

    val model: MediaDetailsViewModel by activityViewModels()

    private val launch: Boolean by lazy { arguments?.getBoolean("launch", false) ?: false }
    private val chp: MangaChapter by lazy { arguments?.getSerialized("next")!! }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        var loaded = false
        binding.selectorAutoListContainer.visibility = View.VISIBLE
        binding.selectorListContainer.visibility = View.GONE

        binding.selectorTitle.text = getString(R.string.loading_chap_number, chp.number)
        binding.selectorCancel.setOnClickListener {
            dismiss()
        }

        model.getMedia().observe(viewLifecycleOwner) { m ->
            if (m != null && !loaded) {
                loaded = true
                binding.selectorAutoText.text = chp.title
                lifecycleScope.launch(Dispatchers.IO) {
                    // Load chapters from the media object
                    val chapters = m.chapters // Assuming m.chapters is a list of MangaChapter
                    val currentChapterIndex = chapters.indexOfFirst { it.number == chp.number }

                    // Check for duplicates and skip them
                    var nextChapterIndex = currentChapterIndex + 1
                    while (nextChapterIndex < chapters.size && chapters[nextChapterIndex].number == chp.number) {
                        nextChapterIndex++
                    }

                    // Ensure we do not go out of bounds
                    if (nextChapterIndex < chapters.size) {
                        val nextChapter = chapters[nextChapterIndex]
                        if (model.loadMangaChapterImages(nextChapter, m.selected!!)) {
                            val activity = currActivity()
                            activity?.runOnUiThread {
                                tryWith { dismiss() }
                                if (launch) {
                                    MediaSingleton.media = m
                                    val intent = Intent(
                                        activity,
                                        MangaReaderActivity::class.java
                                    )
                                    activity.startActivity(intent)
                                }
                            }
                        }
                    } else {
                        // Show dialog when there are no more chapters
                        activity?.runOnUiThread {
                            showNoChapterDialog()
                        }
                    }
                }
            }
        }
    }

    private fun showNoChapterDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("No Chapter")
            .setMessage("There are no more chapters available.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSelectorBinding.inflate(inflater, container, false)
        val window = dialog?.window
        window?.statusBarColor = Color.TRANSPARENT
        window?.navigationBarColor = requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
        return binding.root
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    companion object {
        fun newInstance(next: MangaChapter, launch: Boolean = false) = ChapterLoaderDialog().apply {
            arguments = bundleOf("next" to next as Serializable, "launch" to launch)
        }
    }
}
