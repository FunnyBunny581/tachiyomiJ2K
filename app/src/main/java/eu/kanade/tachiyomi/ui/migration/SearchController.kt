package eu.kanade.tachiyomi.ui.migration

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.catalogue.global_search.CatalogueSearchController
import eu.kanade.tachiyomi.ui.catalogue.global_search.CatalogueSearchPresenter
import uy.kohesive.injekt.injectLazy

class SearchController(
        private var manga: Manga? = null
) : CatalogueSearchController(manga?.title) {

    private var newManga: Manga? = null
    private var progress = 1
    var totalProgress = 0

    override fun getTitle(): String? {
        if (totalProgress > 1) {
            return "($progress/$totalProgress) ${super.getTitle()}"
        }
        else
            return super.getTitle()
    }

    override fun createPresenter(): CatalogueSearchPresenter {
        return SearchPresenter(initialQuery, manga!!)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(::manga.name, manga)
        outState.putSerializable(::newManga.name, newManga)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        manga = savedInstanceState.getSerializable(::manga.name) as? Manga
        newManga = savedInstanceState.getSerializable(::newManga.name) as? Manga
    }

    fun migrateManga() {
        val target = targetController as? MigrationInterface ?: return
        val manga = manga ?: return
        val newManga = newManga ?: return

        val nextManga = target.migrateManga(manga, newManga, true)
        replaceWithNewSearchController(nextManga)
    }

    fun copyManga() {
        val target = targetController as? MigrationInterface ?: return
        val manga = manga ?: return
        val newManga = newManga ?: return

        val nextManga = target.migrateManga(manga, newManga, false)
        replaceWithNewSearchController(nextManga)
    }

    private fun replaceWithNewSearchController(manga: Manga?) {
        if (manga != null) {
            router.popCurrentController()
            val searchController = SearchController(manga)
            searchController.targetController = targetController
            searchController.progress = progress + 1
            searchController.totalProgress = totalProgress
            router.replaceTopController(searchController.withFadeTransaction())
        } else router.popController(this)
    }

    override fun onMangaClick(manga: Manga) {
        newManga = manga
        val dialog = MigrationDialog()
        dialog.targetController = this
        dialog.showDialog(router)
    }

    override fun onMangaLongClick(manga: Manga) {
        // Call parent's default click listener
        super.onMangaClick(manga)
    }

    class MigrationDialog : DialogController() {

        private val preferences: PreferencesHelper by injectLazy()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val prefValue = preferences.migrateFlags().getOrDefault()

            val preselected = MigrationFlags.getEnabledFlagsPositions(prefValue)

            return MaterialDialog.Builder(activity!!)
                    .content(R.string.migration_dialog_what_to_include)
                    .items(MigrationFlags.titles.map { resources?.getString(it) })
                    .alwaysCallMultiChoiceCallback()
                    .itemsCallbackMultiChoice(preselected.toTypedArray(), { _, positions, _ ->
                        // Save current settings for the next time
                        val newValue = MigrationFlags.getFlagsFromPositions(positions)
                        preferences.migrateFlags().set(newValue)

                        true
                    })
                    .positiveText(R.string.migrate)
                    .negativeText(R.string.copy)
                    .neutralText(android.R.string.cancel)
                    .onPositive { _, _ ->
                        (targetController as? SearchController)?.migrateManga()
                    }
                    .onNegative { _, _ ->
                        (targetController as? SearchController)?.copyManga()
                    }
                    .build()
        }

    }

}