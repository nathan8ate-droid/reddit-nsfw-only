/*
 * Reddit NSFW Only - derivative work based on Reddit NSFW Blocker by warleysr.
 * Original: https://github.com/warleysr/reddit-nsfw-blocker
 * Based on Morphe Patches. See NOTICE and LICENSE.
 */
package io.github.redditnsfwonly.extension;

import android.util.Log;
import com.reddit.domain.model.Link;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Always-on patch that keeps mature Reddit content and removes ordinary SFW feed posts. */
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class BlockNsfwContentPatch {
    private static final String TAG = "RedditNsfwOnly";

    private static final String SET_OVER_18_LAMBDA_CLASS =
            "com.reddit.account.repository.RedditPreferenceRepository$setOver18$2";
    private static final long ACCOUNT_SYNC_DELAY_MILLISECONDS = 5000;

    private static volatile Object preferenceRepository;
    private static volatile boolean accountOver18ObservedOff;
    private static final AtomicBoolean accountSyncStarted = new AtomicBoolean();

    public static void setPreferenceRepository(Object repository) {
        if (repository == null || preferenceRepository == repository) return;

        preferenceRepository = repository;
        if (accountOver18ObservedOff) {
            turnOnAccountOver18();
        }
    }

    /**
     * @param over18 The account's current over_18 value.
     * @return Always true so mature content stays enabled in this patched client.
     */
    public static boolean getAccountOver18(boolean over18) {
        if (!over18 && !accountOver18ObservedOff) {
            accountOver18ObservedOff = true;
            turnOnAccountOver18();
        }
        return true;
    }

    private static void turnOnAccountOver18() {
        Object repository = preferenceRepository;
        if (repository == null) return;
        if (!accountSyncStarted.compareAndSet(false, true)) return;

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(ACCOUNT_SYNC_DELAY_MILLISECONDS);
                Log.i(TAG, "Turning on account 'Show mature content'");
                runSetOver18(repository);
            } catch (Exception ex) {
                Log.e(TAG, "Could not turn on account 'Show mature content'", ex);
            }
        }, "reddit-nsfw-only");
        thread.setDaemon(true);
        thread.start();
    }

    /** Runs RedditPreferenceRepository.setOver18(true) through its suspend lambda. */
    private static void runSetOver18(Object repository) throws Exception {
        ClassLoader classLoader = repository.getClass().getClassLoader();
        Class<?> lambdaClass = Class.forName(SET_OVER_18_LAMBDA_CLASS, true, classLoader);

        Constructor<?> constructor = null;
        for (Constructor<?> c : lambdaClass.getDeclaredConstructors()) {
            Class<?>[] types = c.getParameterTypes();
            if (types.length == 3 && types[0].isInstance(repository) && types[1] == boolean.class) {
                constructor = c;
                break;
            }
        }
        if (constructor == null) {
            throw new IllegalStateException("Could not find constructor of " + SET_OVER_18_LAMBDA_CLASS);
        }
        constructor.setAccessible(true);

        Class<?> continuationClass = constructor.getParameterTypes()[2];
        Class<?> coroutineContextClass = Class.forName("kotlin.coroutines.CoroutineContext", true, classLoader);
        Object emptyCoroutineContext = Class.forName("kotlin.coroutines.EmptyCoroutineContext", true, classLoader)
                .getField("INSTANCE").get(null);

        Object completion = Proxy.newProxyInstance(classLoader, new Class<?>[]{continuationClass},
                (proxy, method, args) -> {
                    if (method.getReturnType() == coroutineContextClass) {
                        return emptyCoroutineContext;
                    }
                    switch (method.getName()) {
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "BlockNsfwContentPatch continuation";
                    }
                    if (args != null && args.length == 1) {
                        Log.i(TAG, "Account 'Show mature content' update finished: " + args[0]);
                    }
                    return null;
                });

        Object lambda = constructor.newInstance(repository, true, null);
        Method invoke = lambdaClass.getMethod("invoke", Object.class);
        invoke.setAccessible(true);
        invoke.invoke(lambda, completion);
    }

    /** Keep legacy listing links only when Reddit marks them over18. Non-Link furniture survives. */
    public static List<?> filterLinks(List<?> list) {
        if (list == null || list.isEmpty()) return list;

        try {
            List<Object> filtered = null;
            final int size = list.size();
            for (int i = 0; i < size; i++) {
                Object item = list.get(i);
                if (item instanceof Link link && !link.getOver18()) {
                    if (filtered == null) filtered = new ArrayList<>(list.subList(0, i));
                    Log.d(TAG, "Removing SFW legacy link");
                } else if (filtered != null) {
                    filtered.add(item);
                }
            }
            return filtered == null ? list : filtered;
        } catch (Exception ex) {
            Log.e(TAG, "filterLinks failure", ex);
            return list;
        }
    }

    /**
     * Keep confirmed NSFW items plus structural/unknown objects; remove confirmed SFW feed items.
     * UNKNOWN deliberately fails open so Reddit UI changes do not silently erase the feed.
     */
    public static List<?> filterFeedItems(List<?> list) {
        if (list == null || list.isEmpty()) return list;

        try {
            List<Object> filtered = null;
            final int size = list.size();
            for (int i = 0; i < size; i++) {
                Object item = list.get(i);
                FeedItemClassifier.ContentClass classification = FeedItemClassifier.classify(item);

                if (classification == FeedItemClassifier.ContentClass.SFW) {
                    if (filtered == null) filtered = new ArrayList<>(list.subList(0, i));
                    Log.d(TAG, "Removing SFW feed element: " + className(item));
                } else {
                    if (classification == FeedItemClassifier.ContentClass.UNKNOWN) {
                        Log.d(TAG, "Keeping UNKNOWN feed element for diagnostics: " + className(item));
                    }
                    if (filtered != null) filtered.add(item);
                }
            }
            return filtered == null ? list : filtered;
        } catch (Exception ex) {
            Log.e(TAG, "filterFeedItems failure", ex);
            return list;
        }
    }

    private static String className(Object item) {
        return item == null ? "<null>" : item.getClass().getName();
    }
}
