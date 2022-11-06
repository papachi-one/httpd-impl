package one.papachi.httpd.impl.net;

import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;

public class GenericCompletionHandler<R> implements CompletionHandler<R, Void> {

    private final CompletableFuture<R> completableFuture;

    public GenericCompletionHandler(CompletableFuture<R> completableFuture) {
        this.completableFuture = completableFuture;
    }

    @Override
    public void completed(R result, Void attachment) {
        completableFuture.complete(result);
    }

    @Override
    public void failed(Throwable exc, Void attachment) {
        completableFuture.completeExceptionally(exc);
    }

}
