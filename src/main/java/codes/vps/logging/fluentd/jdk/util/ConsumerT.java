package codes.vps.logging.fluentd.jdk.util;

@FunctionalInterface
public interface ConsumerT<T, E extends Throwable> {

    /**
     * Performs this operation on the given argument.
     *
     * @param t the first input argument
     * @throws E implementation can throw any exception
     */
    void accept(T t) throws E;

}
