package one.papachi.httpd.impl;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

public class PendingReadOperation<A> {

    private final ByteBuffer dst;

    private final A attachment;

    private final CompletionHandler<Integer, ? super A> handler;

    public PendingReadOperation(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        this.dst = dst;
        this.attachment = attachment;
        this.handler = handler;
    }

    public ByteBuffer getDst() {
        return dst;
    }

    public void complete(Integer result) {
        handler.completed(result, attachment);
    }

    public void fail(Throwable t) {
        handler.failed(t, attachment);
    }

}
