package one.papachi.httpd.impl.http.data;


import one.papachi.httpd.api.http.HttpBody;
import one.papachi.httpd.impl.net.AsynchronousInputByteChannel;

import java.io.InputStream;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ReadableByteChannel;

public class DefaultHttpBody extends AsynchronousInputByteChannel implements HttpBody {

    public static class DefaultBuilder implements Builder {

        private Object object;
        private long length = Long.MAX_VALUE;

        @Override
        public Builder empty() {
            object = null;
            return this;
        }

        @Override
        public Builder length(long length) {
            this.length = length;
            return this;
        }

        @Override
        public Builder input(AsynchronousByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder input(AsynchronousFileChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder input(ReadableByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder input(InputStream inputStream) {
            object = inputStream;
            return this;
        }

        @Override
        public HttpBody build() {
            return new DefaultHttpBody(object, length);
        }
    }

    DefaultHttpBody(Object input, long length) {
        super(input, length);
    }

    @Override
    public boolean isPresent() {
        return input != null;
    }

}
