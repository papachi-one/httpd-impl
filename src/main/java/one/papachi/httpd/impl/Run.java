package one.papachi.httpd.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Run {

    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public static void async(Runnable run) {
        executorService.execute(run);
    }

}
