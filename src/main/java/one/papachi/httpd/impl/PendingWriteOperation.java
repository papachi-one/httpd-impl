package one.papachi.httpd.impl;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

public class PendingWriteOperation<A> {

    private final ByteBuffer src;

    private final A attachment;

    private final CompletionHandler<Integer, ? super A> handler;

    public PendingWriteOperation(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        this.src = src;
        this.attachment = attachment;
        this.handler = handler;
    }

    public ByteBuffer getSrc() {
        return src;
    }

    public void complete(Integer result) {
        handler.completed(result, attachment);
    }

    public void fail(Throwable t) {
        handler.failed(t, attachment);
    }

}
