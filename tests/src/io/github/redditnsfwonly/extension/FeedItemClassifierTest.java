package io.github.redditnsfwonly.extension;

import com.reddit.feeds.model.IndicatorType;
import com.reddit.domain.model.Link;
import java.util.ArrayList;
import java.util.List;

public final class FeedItemClassifierTest {
    static final class FeedPost { Object child; FeedPost(Object child) { this.child = child; } }
    static final class FeedHeader { String title = "Popular"; }
    static final class HugePost { List<Object> children = new ArrayList<>(); }
    static final class Node { Object child; Node(Object child) { this.child = child; } }

    private static void expect(Object actual, Object expected, String name) {
        if (!actual.equals(expected)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        expect(FeedItemClassifier.classify(new FeedPost(IndicatorType.NSFW)), FeedItemClassifier.ContentClass.NSFW, "detects nested NSFW enum");
        expect(FeedItemClassifier.classify(new FeedPost(new Link(true))), FeedItemClassifier.ContentClass.NSFW, "detects nested Link over18 flag");
        expect(FeedItemClassifier.classify(new FeedPost(new Link(false))), FeedItemClassifier.ContentClass.SFW, "nested non-over18 Link is SFW");
        expect(FeedItemClassifier.classify(new FeedPost("ordinary post")), FeedItemClassifier.ContentClass.SFW, "ordinary fully scanned post is SFW");
        expect(FeedItemClassifier.classify(new FeedHeader()), FeedItemClassifier.ContentClass.STRUCTURAL, "header survives filtering");

        HugePost huge = new HugePost();
        for (int i = 0; i < 4100; i++) huge.children.add(new Object());
        expect(FeedItemClassifier.classify(huge), FeedItemClassifier.ContentClass.UNKNOWN, "oversized graph fails open as unknown");

        Object deep = "leaf";
        for (int i = 0; i < 12; i++) deep = new Node(deep);
        expect(FeedItemClassifier.classify(deep), FeedItemClassifier.ContentClass.UNKNOWN, "depth-limited graph fails open as unknown");
        expect(FeedItemClassifier.classify(null), FeedItemClassifier.ContentClass.STRUCTURAL, "null is structural");

        System.out.println("FeedItemClassifierTest: PASS");
    }
}
