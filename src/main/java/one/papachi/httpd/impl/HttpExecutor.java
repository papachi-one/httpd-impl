package one.papachi.httpd.impl;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class HttpExecutor {

    static ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() + 1);

}
