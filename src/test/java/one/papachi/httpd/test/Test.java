package one.papachi.httpd.test;

import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Executors;

public class Test {

    public static void main(String[] args) throws Exception {
        AsynchronousChannelGroup group;
        AsynchronousChannelGroup.withThreadPool(Executors.newSingleThreadExecutor());
    }

}
