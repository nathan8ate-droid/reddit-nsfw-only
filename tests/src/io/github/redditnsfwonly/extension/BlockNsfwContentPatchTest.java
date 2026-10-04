package io.github.redditnsfwonly.extension;

import com.reddit.domain.model.Link;
import com.reddit.feeds.model.IndicatorType;
import java.util.Arrays;
import java.util.List;

public final class BlockNsfwContentPatchTest {
    static final class FeedPost { Object marker; FeedPost(Object marker) { this.marker = marker; } }
    static final class FeedHeader { String title = "Home"; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Link nsfw = new Link(true);
        Link sfw = new Link(false);
        Object structural = new Object();
        List<?> legacy = BlockNsfwContentPatch.filterLinks(Arrays.asList(nsfw, sfw, structural));
        require(legacy.contains(nsfw), "legacy: NSFW link must survive");
        require(!legacy.contains(sfw), "legacy: SFW link must be removed");
        require(legacy.contains(structural), "legacy: non-Link structural item must survive");

        FeedPost nsfwPost = new FeedPost(IndicatorType.NSFW);
        FeedPost sfwPost = new FeedPost("ordinary");
        FeedHeader header = new FeedHeader();
        List<?> compose = BlockNsfwContentPatch.filterFeedItems(Arrays.asList(nsfwPost, sfwPost, header));
        require(compose.contains(nsfwPost), "compose: NSFW post must survive");
        require(!compose.contains(sfwPost), "compose: SFW post must be removed");
        require(compose.contains(header), "compose: structural item must survive");

        System.out.println("BlockNsfwContentPatchTest: PASS");
    }
}
