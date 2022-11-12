package one.papachi.httpd.impl.websocket;


import one.papachi.httpd.api.websocket.WebSocketFrame;
import one.papachi.httpd.impl.net.GenericCompletionHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class DefaultWebSocketRemoteFrame implements WebSocketFrame {

    private final boolean isFin, isMask;

    private final Type type;

    private final long length;

    private final byte[] mask;

    private volatile long counter;

    private final AsynchronousByteChannel channel;

    public DefaultWebSocketRemoteFrame(boolean isFin, boolean isMask, Type type, long length, byte[] mask, AsynchronousByteChannel channel) {
        this.isFin = isFin;
        this.isMask = isMask;
        this.type = type;
        this.length = length;
        this.mask = mask;
        this.channel = channel;
    }

    @Override
    public boolean isFin() {
        return isFin;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public boolean isMasked() {
        return isMask;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public byte[] getMask() {
        return mask;
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        channel.read(dst, attachment, handler);
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        read(dst, null, new GenericCompletionHandler<>(completableFuture));
        return completableFuture;
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void close() throws IOException {

    }

}
