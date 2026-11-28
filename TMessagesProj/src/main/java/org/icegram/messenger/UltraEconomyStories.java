package org.icegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.tl.TL_stories;

public final class UltraEconomyStories {
    private UltraEconomyStories() {
    }

    public static boolean isLoadingDisabled() {
        return UltraEconomyController.isEnabled(UltraEconomyController.Option.NO_STORIES);
    }

    public static boolean suppress(TLObject request) {
        if (!isLoadingDisabled()) {
            return false;
        }
        return request instanceof TL_stories.TL_stories_getAllStories
                || request instanceof TL_stories.TL_stories_getPeerStories
                || request instanceof TL_stories.TL_stories_getPinnedStories
                || request instanceof TL_stories.TL_stories_getStoriesArchive
                || request instanceof TL_stories.TL_stories_getStoryViewsList
                || request instanceof TL_stories.TL_stories_getStoriesByID
                || request instanceof TL_stories.TL_stories_getStoriesViews
                || request instanceof TL_stories.TL_stories_getAllReadPeerStories
                || request instanceof TL_stories.TL_stories_getPeerMaxIDs
                || request instanceof TL_stories.TL_stories_searchPosts
                || request instanceof TL_stories.TL_getAlbums
                || request instanceof TL_stories.TL_stories_getAlbumStories
                || request instanceof TL_stories.TL_getStoryReactionsList
                || request instanceof TL_stories.TL_stats_getStoryStats;
    }
}
