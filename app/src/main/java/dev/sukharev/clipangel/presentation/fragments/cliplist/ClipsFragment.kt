package dev.sukharev.clipangel.presentation.fragments.cliplist

import android.os.Bundle
import android.view.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.sukharev.clipangel.R
import dev.sukharev.clipangel.presentation.ToolbarPresenter
import dev.sukharev.clipangel.presentation.fragments.BaseFragment
import dev.sukharev.clipangel.presentation.fragments.bottom.ListBottomDialogFragment
import dev.sukharev.clipangel.presentation.fragments.bottom.SingleListAdapter
import dev.sukharev.clipangel.presentation.models.Category
import dev.sukharev.clipangel.presentation.view.info.InformationView
import dev.sukharev.clipangel.presentation.viewmodels.channellist.MainViewModel
import dev.sukharev.clipangel.utils.copyInClipboardWithToast
import org.koin.android.ext.android.inject


class ClipsFragment: BaseFragment(), OnClipItemClickListener {

    private var emptyClipList: InformationView? = null
    private var errorLayout: InformationView? = null
    private var contentLayout: ConstraintLayout? = null

    private lateinit var mainViewModel: MainViewModel

    private val detailedClipObserver = Observer<ClipListViewModel.DetailedClipModel> {
                DetailClipDialogFragment(it).show(childFragmentManager, "clip_detail_bottom_dialog")
    }

    private val permitClipAccessObserver = Observer<String> {
        mainViewModel.openBiometryDialogForClip(it)
    }

    private val clipListAdapter = ClipListAdapter {
        viewModel.createClipAction(ClipListViewModel.ClipAction.Copy(it, false))
    }.apply {
        onItemCLickListener = this@ClipsFragment
    }

    private val viewModel: ClipListViewModel by inject()

    override fun initToolbar(presenter: ToolbarPresenter) {
        presenter.getToolbar()?.apply {
            title = "Список клипов"
            navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_category)
            setNavigationIconTint(requireContext().getColor(R.color.pantone_light_green))

            setNavigationOnClickListener {
                createCategoryTypesDialog(viewModel.categoryTypeLiveData.value!!).show(childFragmentManager, "list_bottom")
            }
        }
    }

    private fun createCategoryTypesDialog(selectedCategory: Category): ListBottomDialogFragment {
        val items = listOf(
                ListBottomDialogFragment.ListItem(Category.All().id, getString(R.string.all),
                        R.drawable.ic_list_2),
                ListBottomDialogFragment.ListItem(Category.Favorite().id, getString(R.string.favorite),
                        R.drawable.ic_star),
                ListBottomDialogFragment.ListItem(Category.Private().id, getString(R.string.secured),
                        R.drawable.ic_lock)
        )

        // Mark category as selected
        items.find { it.id == selectedCategory.id }?.isSelected = true

        return ListBottomDialogFragment(items, getString(R.string.categories)).also { dialog ->
            dialog.setOnItemClickListener(object : SingleListAdapter.OnItemClickListener {
                override fun onClick(item: ListBottomDialogFragment.ListItem) {
                    Category.getById(item.id)?.also {
                        dialog.dismiss()
                        viewModel.changeCategoryType(it)
                    }
                }
            })
        }
    }


    override fun showBottomNavigation(): Boolean = true

    private val errorObserver = Observer<Throwable> {
        if (it == null)
            errorLayout?.visibility = View.GONE
        else {
            errorLayout?.visibility = View.VISIBLE
        }
    }

    private val clipListObserver = Observer<List<ClipItemViewHolder.Model>> {
        if (it.isEmpty()) {
            emptyClipList?.visibility = View.VISIBLE
            contentLayout?.visibility = View.GONE
        } else {
            clipListAdapter.setItems(it)
            emptyClipList?.visibility = View.GONE
            contentLayout?.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        getNavDrawer().enabled(true)
        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
    }

    private fun setToolbarTitleByCategory(category: Category) {
        getToolbarPresenter().apply {
            setTitle(when (category) {
                is Category.All -> getString(R.string.category_all)
                is Category.Favorite -> getString(R.string.category_favorite)
                is Category.Private -> getString(R.string.categoty_protected)
            })
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.clip_list, menu)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        return inflater.inflate(R.layout.fragment_clip_list, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyClipList = view.findViewById(R.id.empty_content_template)
        errorLayout = view.findViewById(R.id.error_view)
        contentLayout = view.findViewById(R.id.content_layout)

        viewModel.categoryTypeLiveData.observe(viewLifecycleOwner) {
            setToolbarTitleByCategory(it)
        }

        viewModel.detailedClip.observe(viewLifecycleOwner, detailedClipObserver)
        viewModel.errorLiveData.observe(viewLifecycleOwner, errorObserver)
        viewModel.clipItemsLiveData.observe(viewLifecycleOwner, clipListObserver)
        viewModel.permitClipAccessLiveData.observe(viewLifecycleOwner, permitClipAccessObserver)
        viewModel.copyClipData.observe(viewLifecycleOwner) {
            it.copyInClipboardWithToast(getString(R.string.copied_alert))
        }

        mainViewModel.permitAccessForClip.observe(viewLifecycleOwner) {
            it?.let { clipId ->
                val action: ClipListViewModel.ClipAction? = viewModel.clipAction.value

                when(action) {
                    is ClipListViewModel.ClipAction.ShowDetail ->
                        viewModel.createClipAction(ClipListViewModel.ClipAction.ShowDetail(clipId, true))

                    is ClipListViewModel.ClipAction.Copy -> {
                        viewModel.copyClip(clipId)
                    }
                }

                mainViewModel.permitAccessForClip.value = null
            }
        }

        view.findViewById<RecyclerView>(R.id.clip_list_recycler)?.apply {
            layoutManager = LinearLayoutManager(view.context)
            adapter = clipListAdapter
        }


        viewModel.loadClips()
    }

    override fun onItemClicked(id: String) {
        viewModel.createClipAction(ClipListViewModel.ClipAction.ShowDetail(id, false))
    }

}