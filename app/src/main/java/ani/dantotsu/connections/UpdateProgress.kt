package ani.dantotsu.connections

import ani.dantotsu.R
import ani.dantotsu.Refresh
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.currContext
import ani.dantotsu.media.Media
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun updateProgress(media: Media, number: String) {
    val incognito: Boolean = PrefManager.getVal(PrefName.Incognito)
    if (!incognito) {
        if (Anilist.userid != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val newProgress = number.toFloatOrNull()?.toInt() ?: 0

                // Update Anilist and MAL if progress has increased or if the entry is being removed
                if (newProgress > (media.userProgress ?: -1) || newProgress == 0) {
                    Anilist.mutation.editList(
                        media.id,
                        newProgress,
                        status = if (media.userStatus == "REPEATING") media.userStatus else "CURRENT"
                    )

                    MAL.query.editList(
                        media.idMAL,
                        media.anime != null,
                        newProgress,
                        null,
                        if (media.userStatus == "REPEATING") media.userStatus!! else "CURRENT"
                    )

                    if (newProgress == 0) {
                        // Remove the entry from MAL if progress is set to 0
                        MAL.query.deleteList(media.anime != null, media.idMAL)
                    }

                    toast(currContext()?.getString(R.string.setting_progress, newProgress))
                }

                media.userProgress = newProgress
                Refresh.all()
            }
        } else {
            toast(currContext()?.getString(R.string.login_anilist_account))
        }
    } else {
        toast("Sneaky sneaky :3")
    }
}
