package codes.vps.logging.fluentd.jdk.sample;

import codes.vps.logging.fluentd.jdk.FluentdHandler;

import java.util.logging.LogManager;
import java.util.logging.Logger;

public class CreateHandler {

    public static void main(String[] a) {

        FluentdHandler.Builder b = new FluentdHandler.Builder();
        b.setHost("localhost").setTagPrefix("my-stuff");
        Logger log = LogManager.getLogManager().getLogger("my-stuff");
        log.addHandler(new FluentdHandler(b));
        log.warning("All your base are belong to us");

    }

}
