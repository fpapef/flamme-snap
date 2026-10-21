package kotlin.comparisons;

import java.util.Comparator;
import kotlin.jvm.functions.Function1;

/**
 * Remplacement de la classe du même nom de kotlin-stdlib 1.9.24.
 * L'original utilise invokedynamic (lambdas Java 8), que l'outil dx refuse pour
 * Android 7. Même contenu public et même comportement, écrit avec des classes
 * anonymes. Les méthodes privées « inline-only » de l'original ne sont jamais
 * appelées depuis l'extérieur et ne sont pas reprises.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ComparisonsKt__ComparisonsKt {

    public ComparisonsKt__ComparisonsKt() {
    }

    private static void require(boolean ok) {
        if (!ok) throw new IllegalArgumentException("Failed requirement.");
    }

    private static <T> int compareValuesByImpl(T a, T b, Function1[] selectors) {
        for (Function1 fn : selectors) {
            Comparable v1 = (Comparable) fn.invoke(a);
            Comparable v2 = (Comparable) fn.invoke(b);
            int diff = compareValues(v1, v2);
            if (diff != 0) return diff;
        }
        return 0;
    }

    public static final <T> int compareValuesBy(T a, T b, Function1<? super T, ? extends Comparable<?>>... selectors) {
        if (selectors == null) throw new NullPointerException("Parameter specified as non-null is null: selectors");
        require(selectors.length > 0);
        return compareValuesByImpl(a, b, selectors);
    }

    public static final <T extends Comparable<?>> int compareValues(T a, T b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return ((Comparable<Object>) a).compareTo(b);
    }

    public static final <T> Comparator<T> compareBy(final Function1<? super T, ? extends Comparable<?>>... selectors) {
        if (selectors == null) throw new NullPointerException("Parameter specified as non-null is null: selectors");
        require(selectors.length > 0);
        return new Comparator<T>() {
            @Override
            public int compare(T a, T b) {
                return compareValuesByImpl(a, b, selectors);
            }
        };
    }

    public static final <T> Comparator<T> then(final Comparator<T> self, final Comparator<? super T> comparator) {
        if (self == null) throw new NullPointerException("Parameter specified as non-null is null: <this>");
        if (comparator == null) throw new NullPointerException("Parameter specified as non-null is null: comparator");
        return new Comparator<T>() {
            @Override
            public int compare(T a, T b) {
                int previous = self.compare(a, b);
                return previous != 0 ? previous : comparator.compare(a, b);
            }
        };
    }

    public static final <T> Comparator<T> thenDescending(final Comparator<T> self, final Comparator<? super T> comparator) {
        if (self == null) throw new NullPointerException("Parameter specified as non-null is null: <this>");
        if (comparator == null) throw new NullPointerException("Parameter specified as non-null is null: comparator");
        return new Comparator<T>() {
            @Override
            public int compare(T a, T b) {
                int previous = self.compare(a, b);
                return previous != 0 ? previous : comparator.compare(b, a);
            }
        };
    }

    public static final <T> Comparator<T> nullsFirst(final Comparator<? super T> comparator) {
        if (comparator == null) throw new NullPointerException("Parameter specified as non-null is null: comparator");
        return new Comparator<T>() {
            @Override
            public int compare(T a, T b) {
                if (a == b) return 0;
                if (a == null) return -1;
                if (b == null) return 1;
                return comparator.compare(a, b);
            }
        };
    }

    public static final <T> Comparator<T> nullsLast(final Comparator<? super T> comparator) {
        if (comparator == null) throw new NullPointerException("Parameter specified as non-null is null: comparator");
        return new Comparator<T>() {
            @Override
            public int compare(T a, T b) {
                if (a == b) return 0;
                if (a == null) return 1;
                if (b == null) return -1;
                return comparator.compare(a, b);
            }
        };
    }

    public static final <T extends Comparable<? super T>> Comparator<T> naturalOrder() {
        return (Comparator) NaturalOrderComparator.INSTANCE;
    }

    public static final <T extends Comparable<? super T>> Comparator<T> reverseOrder() {
        return (Comparator) ReverseOrderComparator.INSTANCE;
    }

    public static final <T> Comparator<T> reversed(Comparator<T> self) {
        if (self == null) throw new NullPointerException("Parameter specified as non-null is null: <this>");
        if (self instanceof ReversedComparator) return ((ReversedComparator<T>) self).getComparator();
        if (self == (Object) NaturalOrderComparator.INSTANCE) return (Comparator) ReverseOrderComparator.INSTANCE;
        if (self == (Object) ReverseOrderComparator.INSTANCE) return (Comparator) NaturalOrderComparator.INSTANCE;
        return new ReversedComparator<T>(self);
    }
}
