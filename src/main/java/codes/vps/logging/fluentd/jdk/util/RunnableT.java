package codes.vps.logging.fluentd.jdk.util;

@FunctionalInterface
public interface RunnableT<X extends Throwable> {

    void run() throws X;

}
