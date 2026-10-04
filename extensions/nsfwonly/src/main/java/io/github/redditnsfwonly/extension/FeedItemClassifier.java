package io.github.redditnsfwonly.extension;

import com.reddit.domain.model.Link;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class FeedItemClassifier {
    enum ContentClass { NSFW, SFW, STRUCTURAL, UNKNOWN }

    private static final Set<String> NSFW_ENUM_CLASSES = new HashSet<>(Arrays.asList(
            "com.reddit.feeds.model.IndicatorType",
            "com.reddit.domain.media.MediaBlurType"
    ));
    private static final String NSFW_ENUM_NAME = "NSFW";

    private static final String[] STRUCTURAL_CLASS_HINTS = {
            "header", "footer", "divider", "separator", "spacer", "loading",
            "skeleton", "pagination", "sortbar", "filterbar", "navigation", "toolbar"
    };

    private static final String[] IGNORED_PACKAGE_PREFIXES = {
            "java.", "javax.", "android.", "androidx.", "kotlin.", "kotlinx.",
            "com.google.", "okhttp3.", "okio.", "dalvik.", "sun."
    };

    private static final int MAX_DEPTH = 8;
    private static final int MAX_VISITED_OBJECTS = 4000;

    private static final Map<Class<?>, Field[]> fieldCache = new ConcurrentHashMap<>();
    private static final Field[] NO_FIELDS = new Field[0];

    static ContentClass classify(Object root) {
        if (root == null) return ContentClass.STRUCTURAL;
        if (looksStructural(root.getClass())) return ContentClass.STRUCTURAL;

        try {
            return scanForNsfw(root) ? ContentClass.NSFW : ContentClass.SFW;
        } catch (ScanIncompleteException ex) {
            return ContentClass.UNKNOWN;
        } catch (RuntimeException ex) {
            return ContentClass.UNKNOWN;
        }
    }

    private static boolean scanForNsfw(Object root) {
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        Deque<Object> stack = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        stack.push(root);
        depths.push(0);
        boolean truncated = false;

        while (!stack.isEmpty()) {
            Object obj = stack.pop();
            int depth = depths.pop();

            if (visited.put(obj, Boolean.TRUE) != null) continue;
            if (visited.size() > MAX_VISITED_OBJECTS) throw new ScanIncompleteException();

            if (obj instanceof Enum<?> e) {
                if (isNsfwEnum(e)) return true;
                continue;
            }

            if (obj instanceof Link link) {
                if (link.getOver18()) return true;
                continue;
            }

            if (depth >= MAX_DEPTH) {
                truncated = true;
                continue;
            }
            int childDepth = depth + 1;

            if (obj instanceof Collection<?> collection) {
                if (isEnumCatalog(collection)) continue;
                for (Object child : collection) push(child, childDepth, stack, depths);
                continue;
            }

            if (obj instanceof Map<?, ?> map) {
                for (Object child : map.values()) push(child, childDepth, stack, depths);
                continue;
            }

            Class<?> clazz = obj.getClass();
            if (clazz.isArray()) {
                if (clazz.getComponentType().isPrimitive()) continue;
                for (int i = 0; i < Array.getLength(obj); i++) {
                    push(Array.get(obj, i), childDepth, stack, depths);
                }
                continue;
            }

            if (obj instanceof Iterable<?> iterable) {
                for (Object child : iterable) push(child, childDepth, stack, depths);
                continue;
            }

            for (Field field : getFields(clazz)) {
                try {
                    push(field.get(obj), childDepth, stack, depths);
                } catch (IllegalAccessException ex) {
                    throw new ScanIncompleteException();
                }
            }
        }

        if (truncated) throw new ScanIncompleteException();
        return false;
    }

    private static void push(Object child, int depth, Deque<Object> stack, Deque<Integer> depths) {
        if (child == null) return;
        stack.push(child);
        depths.push(depth);
    }

    private static boolean looksStructural(Class<?> clazz) {
        String simple = clazz.getSimpleName().toLowerCase();
        for (String hint : STRUCTURAL_CLASS_HINTS) {
            if (simple.contains(hint)) return true;
        }
        return false;
    }

    private static boolean isNsfwEnum(Enum<?> e) {
        return NSFW_ENUM_NAME.equals(e.name())
                && NSFW_ENUM_CLASSES.contains(e.getDeclaringClass().getName());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isEnumCatalog(Collection<?> collection) {
        if (collection.size() < 2) return false;

        Class<? extends Enum> enumClass = null;
        for (Object item : collection) {
            if (item instanceof Enum<?> e && NSFW_ENUM_CLASSES.contains(e.getDeclaringClass().getName())) {
                enumClass = e.getDeclaringClass();
                break;
            }
        }
        if (enumClass == null) return false;

        EnumSet found = EnumSet.noneOf(enumClass);
        for (Object item : collection) {
            if (enumClass.isInstance(item)) found.add((Enum) item);
        }
        return found.size() == enumClass.getEnumConstants().length;
    }

    private static Field[] getFields(Class<?> clazz) {
        Field[] cached = fieldCache.get(clazz);
        if (cached != null) return cached;

        if (isIgnoredClass(clazz)) {
            fieldCache.put(clazz, NO_FIELDS);
            return NO_FIELDS;
        }

        java.util.ArrayList<Field> list = new java.util.ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class && !isIgnoredClass(c); c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers)) continue;
                Class<?> type = field.getType();
                if (type.isPrimitive() || type == String.class) continue;
                try {
                    field.setAccessible(true);
                    list.add(field);
                } catch (RuntimeException ex) {
                    throw new ScanIncompleteException();
                }
            }
        }
        Field[] fields = list.toArray(NO_FIELDS);
        fieldCache.put(clazz, fields);
        return fields;
    }

    private static boolean isIgnoredClass(Class<?> clazz) {
        if (clazz.isSynthetic()) return true;

        String name = clazz.getName();
        for (String prefix : IGNORED_PACKAGE_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }

        for (Class<?> anInterface : clazz.getInterfaces()) {
            String interfaceName = anInterface.getName();
            if (interfaceName.startsWith("kotlin.jvm.functions.Function")
                    || interfaceName.equals("kotlin.Function")) {
                return true;
            }
        }
        return false;
    }

    private static final class ScanIncompleteException extends RuntimeException {}
}
