package codes.vps.logging.fluentd.jdk.util;

/**
 * Analog of {@link java.util.function.Supplier}, but enables throwing
 * an exception when producing objects.
 * @param <T> type returned from the supplier
 * @param <E> exception that can be thrown from the get() method
 */
@FunctionalInterface
public interface SupplierT<T, E extends Throwable> {

    /**
     * Gets a result.
     *
     * @return a result
     * @throws E any exception
     */
    T get() throws E;
}
